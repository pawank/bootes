package com.bootes.server

import com.bootes.dao.UserService
import com.bootes.dao.repository.{NotFoundException, UserRepository}
import com.bootes.server.auth.AuthenticationApp
import zhttp.http.{Response, _}
import zhttp.service.Server
import zio._
import zio.console._
import zio.duration.durationInt
import zio.logging.Logging
import zio.magic._
import zio.clock.Clock
import zio.logging.{LogAnnotation, LogFormat, LogLevel}

import java.util.UUID
import com.github.mvv.sredded.StructuredMapping
import com.github.mvv.sredded.generic.deriveStructured
import com.github.mvv.sredded.StructuredMapping
import com.github.mvv.sredded.generic.deriveStructured
import com.github.mvv.zilog.{Logger => ZLogger, Logging => ZLogging}
import zio.config.derivation.name


final case class ClientRequest(src: String, method: String, path: String)
object ClientRequest {
  implicit val structured: StructuredMapping[ClientRequest] = deriveStructured
}
object RequestKey extends ZLogging.Key[ClientRequest]("request")
object CorrelationIdKey extends ZLogging.Key[String]("correlationId")
object CustomerIdKey extends ZLogging.Key[Long]("customerId")

object UserServer extends App {
  implicit val logger: ZLogger = ZLogger[UserServer.type]

  final val CorrelationId: LogAnnotation[Option[UUID]] = LogAnnotation[Option[UUID]](
    name = "correlation-id",
    initialValue = None,
    combine = (_, r) => r,
    render = _.map(_.toString).getOrElse("undefined-correlation-id")
  )

  final val DebugJsonLog: LogAnnotation[String] = LogAnnotation[String](
    name = "debug-json-log",
    initialValue = "",
    combine = (_, r) => r,
    render = _.toString
  )

  final val logEnv: ZLayer[zio.console.Console with Clock, Nothing, Logging] =
    Logging.console(
      logLevel = LogLevel.Debug,
      format = LogFormat.ColoredLogFormat((ctx, line) => s"${ctx(CorrelationId)} ${ctx(DebugJsonLog)} [$line]")
    ) >>>
      Logging.withRootLoggerName(s"UserServer")

  val logLayer: TaskLayer[Logging] = ZEnv.live >>> logEnv

  val root: Path = Root / "bootes" / "v1"
  private def getVersion(root: Path):Http[Any, Nothing, Request, UResponse] = Http.collect[Request] {
    case Method.GET -> `root` / "version" => {
      val version = "0.1"
      scribe.info(s"Service version = $version")
      Response.jsonString(s"""{"version": "$version"}""")
    }
  }

  val endpoints: Http[Has[UserService] with Clock with Console with Logging with ZLogging with system.System, HttpError, Request, Response[Has[UserService] with Console with Logging with ZLogging, HttpError]] =
    getVersion(root) +++ AuthenticationApp.login +++ CORS(
      AuthenticationApp.authenticate(HttpApp.forbidden("None shall pass."), UserEndpoints.user),
      config = CORSConfig(anyOrigin = true)
    ) +++ InvoiceEndpoints.invoiceRoutes

  val program: ZIO[Any, Throwable, Nothing] = {
    import sttp.client3._
    import sttp.client3.asynchttpclient.zio._
    scribe.info("Starting bootes service..")
    Server
      .start(8080, endpoints)
      .inject(Console.live, logLayer, Clock.live, ZLogging.consoleJson(), AsyncHttpClientZioBackend.layer(), UserService.layerKeycloakService, system.System.live)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode
}
