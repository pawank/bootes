package com.bootes.server

import com.bootes.dao.UserService
import com.bootes.dao.repository.{NotFoundException, UserRepository}
import com.bootes.server.auth.AuthenticationApp
import zhttp.http._
import zhttp.service.Server
import zio._
import zio.console._
import zio.duration.durationInt
import zio.logging.Logging
import zio.magic._
/*
import com.github.mvv.sredded.StructuredMapping
import com.github.mvv.sredded.generic.deriveStructured
import com.github.mvv.sredded.StructuredMapping
import com.github.mvv.sredded.generic.deriveStructured
import com.github.mvv.zilog._
import zio.config.derivation.name
 */
import zio.clock.Clock
import zio.logging.{LogAnnotation, LogFormat, LogLevel}

import java.util.UUID

/*
final case class ClientRequest(src: String, method: String, path: String)
object ClientRequest {
  implicit val structured: StructuredMapping[ClientRequest] = deriveStructured
}

object RequestKey extends Logging.Key[ClientRequest]("request")
object CorrelationIdKey extends Logging.Key[String]("correlationId")
object CustomerIdKey extends Logging.Key[Long]("customerId")
*/

object UserServer extends App {
  //implicit val logger: Logger = Logger[UserServer.type]

  final val CalculationId: LogAnnotation[Option[UUID]] = LogAnnotation[Option[UUID]](
    name = "calculation-id",
    initialValue = None,
    combine = (_, r) => r,
    render = _.map(_.toString).getOrElse("undefined-calculation-id")
  )

  final val CalculationNumber: LogAnnotation[Int] = LogAnnotation[Int](
    name = "calculation-number",
    initialValue = 0,
    combine = (_, r) => r,
    render = _.toString
  )

  final val logEnv: ZLayer[zio.console.Console with Clock, Nothing, Logging] =
    Logging.console(
      logLevel = LogLevel.Debug,
      format = LogFormat.ColoredLogFormat((ctx, line) => s"${ctx(CalculationId)} ${ctx(CalculationNumber)} $line")
    ) >>>
      Logging.withRootLoggerName("my-logger")

  val logLayer: TaskLayer[Logging] = ZEnv.live >>> logEnv

  val endpoints: Http[Has[UserService] with Console with Logging, HttpError, Request, Response[Has[UserService] with Console with Logging, HttpError]] =
    AuthenticationApp.login +++ CORS(
      AuthenticationApp.authenticate(HttpApp.forbidden("None shall pass."), UserEndpoints.user),
      config = CORSConfig(anyOrigin = true)
    ) +++ InvoiceEndpoints.invoiceRoutes

  val program: ZIO[Any, Throwable, Nothing] = {
    import sttp.client3._
    import sttp.client3.asynchttpclient.zio._
    Server
      .start(8080, endpoints)
      .inject(Console.live, logLayer, AsyncHttpClientZioBackend.layer(), UserService.layerKeycloakService)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode
}
