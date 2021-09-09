package com.bootes.server

import com.bootes.dao.keycloak.Models.ServiceContext
import com.bootes.dao.{UserService, ZioQuillContext}
import com.bootes.dao.repository.{NotFoundException, UserRepository}
import com.bootes.server.UserServer.logLayer
import com.bootes.server.auth.AuthenticationApp
import com.data2ui.FormService
import com.data2ui.repository.{FormElementsRepository, FormRepository, FormRepositoryLive, OptionsRepository, OptionsRepositoryLive, ValidationsRepository}
import com.data2ui.server.FormEndpoints
import zhttp.http.{Response, _}
import zhttp.service.{EventLoopGroup, Server}
import zio._
import zio.console._
import zio.duration.durationInt
import zio.logging.{LogAnnotation, LogFormat, LogLevel, Logging, log}
import zio.magic._
import zio.clock.Clock

import java.util.UUID
import zhttp.service.server.ServerChannelFactory
import zio.zmx._
import zio.zmx.diagnostics._

import java.io.IOException


object UserServer extends App {
  final val CorrelationId: LogAnnotation[UUID] = LogAnnotation[UUID](
    name = "correlation-id",
    initialValue = UUID.randomUUID(),
    combine = (_, r) => r,
    render = _.toString
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

  val userEndpoints: Http[Has[UserService] with Clock with Console with Logging with system.System, HttpError, Request, Response[Has[UserService]  with Console with Logging, HttpError]] =
    CORS(
      AuthenticationApp.authenticate(HttpApp.forbidden("Oops! You are not authorised to access the requested feature. Please check your credentials."), UserEndpoints.user),
      config = getCorsConfig()
    )

  val formEndpoints: Http[Has[FormService] with Clock with Console with Logging with system.System, HttpError, Request, Response[Has[FormService] with Console with Logging, HttpError]] =
    CORS(
        AuthenticationApp.authenticate(HttpApp.forbidden("Oops! You are not authorised to access the requested feature. Please check your credentials."), FormEndpoints.form),
      config = getCorsConfig()
    )

  def checkAndAllowedOrigins(origin: String): Boolean = origin.equalsIgnoreCase("*")

  def getCorsConfig(): CORSConfig = {
    //CORSConfig(anyOrigin = true, anyMethod = true, exposedHeaders = Some(Set("X-Requested-With", "Content-Type", "Authorization", "Accept", "Origin")), allowedHeaders = Some(Set("X-Requested-With", "Content-Type", "Authorization", "Accept", "Origin")), allowedMethods = Some(Set(zhttp.http.Method.HEAD, zhttp.http.Method.PATCH, zhttp.http.Method.OPTIONS, zhttp.http.Method.GET, zhttp.http.Method.POST, zhttp.http.Method.PUT, zhttp.http.Method.DELETE)))
    CORSConfig(anyOrigin = true, anyMethod = true, exposedHeaders = Some(Set("X-XSS-Protection", "X-Requested-With", "Content-Type", "Authorization", "Accept", "Origin")), allowedHeaders = Some(Set("X-Requested-With", "Content-Type", "Authorization", "Accept", "Origin")), allowedMethods = Some(Set(zhttp.http.Method.HEAD, zhttp.http.Method.PATCH, zhttp.http.Method.OPTIONS, zhttp.http.Method.GET, zhttp.http.Method.POST, zhttp.http.Method.PUT, zhttp.http.Method.DELETE)), allowedOrigins = checkAndAllowedOrigins)
  }

  val program: ZIO[Any with Console, Throwable, Nothing] = {
    import sttp.client3._
    import sttp.client3.asynchttpclient.zio._
    val port = 8080
    //val app = getVersion(root) +++ AuthenticationApp.login +++ userEndpoints +++ formEndpoints
    val app = CORS(getVersion(root) +++ AuthenticationApp.login, config = getCorsConfig()) +++ userEndpoints +++ formEndpoints
    val server = Server.port(port) ++ Server.app(app) ++ Server.maxRequestSize(4194304)
    for {
      _ <- putStrLn(s"Starting the server at port, $port")
      s <- server.start.inject(ServerChannelFactory.auto, EventLoopGroup.auto(0), Console.live, ZioQuillContext.dataSourceLayer, OptionsRepository.layer, ValidationsRepository.layer, FormElementsRepository.layer, logLayer, Clock.live, AsyncHttpClientZioBackend.layer(), UserService.layerKeycloakService, FormRepository.layer, FormService.layer, system.System.live)
      _ <- putStrLn(s"Shutting down the server at port, $port")
    } yield s
  }

  val diagnosticsLayer: ZLayer[ZEnv, Throwable, Has[Diagnostics]] =
    Diagnostics.make("localhost", 1111)

  val runtime: Runtime[ZEnv] =
    Runtime.default.mapPlatform(_.withSupervisor(ZMXSupervisor))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    runtime.unsafeRun(program.provideCustomLayer(diagnosticsLayer))
    //program.exitCode
  }
}
