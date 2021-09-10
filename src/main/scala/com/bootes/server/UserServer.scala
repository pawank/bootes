package com.bootes.server

import com.bootes.config.config.AppConfig
import com.bootes.dao.keycloak.Models.ServiceContext
import com.bootes.dao.{UserService, ZioQuillContext}
import com.bootes.dao.repository.{NotFoundException, UserRepository}
import com.bootes.server.UserServer.logLayer
import com.bootes.server.auth.AuthenticationApp
import com.bootes.tracking.JaegerTracer
import com.data2ui.FormService
import com.data2ui.repository.{FormElementsRepository, FormRepository, FormRepositoryLive, OptionsRepository, OptionsRepositoryLive, ValidationsRepository}
import com.data2ui.server.FormEndpoints
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{TextMapGetter, TextMapPropagator}
import sttp.model.Uri
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
import zio.config.getConfig
import zio.config.magnolia.{Descriptor, descriptor}
import zio.config.typesafe.TypesafeConfig
import zio.json.{DeriveJsonCodec, EncoderOps, JsonCodec}
import zio.telemetry.opentelemetry.Tracing
import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps
import zio.zmx.MetricSnapshot.Prometheus
import zio.zmx._
import zio.zmx.diagnostics._
import zio.zmx.metrics.{MetricAspect, MetricsSyntax}
import zio.zmx.prometheus.PrometheusClient

import java.io.IOException
import java.lang
import scala.jdk.CollectionConverters._

final case class Status(name: String, status: String)

object Status {
  implicit val codec: JsonCodec[Status] = DeriveJsonCodec.gen[Status]

  final def up(component: String): Status   = Status(component, status = "up")
  final def down(component: String): Status = Status(component, status = "down")

}

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

  val propagator: TextMapPropagator       = W3CTraceContextPropagator.getInstance()
  val getter: TextMapGetter[List[Header]] = new TextMapGetter[List[Header]] {
    override def keys(carrier: List[Header]): lang.Iterable[String] =
      carrier.map(_.name.toString).asJava

    override def get(carrier: List[Header], key: String): String =
      carrier.find(_.name.toString == key).map(_.value.toString).orNull
  }
  val routes: HttpApp[Tracing, Throwable] =
    Http.collectM { case request @ Method.GET -> Root / "status" =>
      val response = for {
        _        <- Tracing.addEvent("event from backend before response")
        response <- ZIO.succeed(Response.jsonString(Status.up("backend").toJson))
        _        <- Tracing.addEvent("event from backend after response")
      } yield response

      response.spanFrom(propagator, request.headers, getter, "/status", SpanKind.SERVER)
    }

  private lazy val metricsEndpoint = Http.collectM[Request] {
    case Method.GET -> Root / "metrics" =>
      PrometheusClient.snapshot.map { case Prometheus(value) => Response.text(value) }
  }

  def checkAndAllowedOrigins(origin: String): Boolean = origin.equalsIgnoreCase("*")

  def getCorsConfig(): CORSConfig = {
    //CORSConfig(anyOrigin = true, anyMethod = true, exposedHeaders = Some(Set("X-Requested-With", "Content-Type", "Authorization", "Accept", "Origin")), allowedHeaders = Some(Set("X-Requested-With", "Content-Type", "Authorization", "Accept", "Origin")), allowedMethods = Some(Set(zhttp.http.Method.HEAD, zhttp.http.Method.PATCH, zhttp.http.Method.OPTIONS, zhttp.http.Method.GET, zhttp.http.Method.POST, zhttp.http.Method.PUT, zhttp.http.Method.DELETE)))
    CORSConfig(anyOrigin = true, anyMethod = true, exposedHeaders = Some(Set("X-XSS-Protection", "X-Requested-With", "Content-Type", "Authorization", "Accept", "Origin")), allowedHeaders = Some(Set("X-Requested-With", "Content-Type", "Authorization", "Accept", "Origin")), allowedMethods = Some(Set(zhttp.http.Method.HEAD, zhttp.http.Method.PATCH, zhttp.http.Method.OPTIONS, zhttp.http.Method.GET, zhttp.http.Method.POST, zhttp.http.Method.PUT, zhttp.http.Method.DELETE)), allowedOrigins = checkAndAllowedOrigins)
  }

  val aspServerStartCountAll = MetricAspect.count("serverRunAll")

  implicit val sttpUriDescriptor: Descriptor[Uri] =
    Descriptor[String].transformOrFailLeft(Uri.parse)(_.toString)
  val configLayer = TypesafeConfig.fromDefaultLoader(descriptor[AppConfig])

  val program: ZIO[Any with Console with Has[AppConfig], Throwable, Nothing] = {
    import sttp.client3._
    import sttp.client3.asynchttpclient.zio._
    //val port = 8080
    val app = CORS(getVersion(root) +++ metricsEndpoint +++ AuthenticationApp.login, config = getCorsConfig()) +++ userEndpoints +++ formEndpoints

    for {
      conf <- getConfig[AppConfig]
      backendPort = conf.backend.host.port.getOrElse(8000)
      _ <- putStrLn(s"Starting the server at port, $backendPort")
      server = Server.port(backendPort) ++ Server.app(app +++ routes) ++ Server.maxRequestSize(4194304)
      s <- server.start.inject(ServerChannelFactory.auto, EventLoopGroup.auto(0), Clock.live, configLayer, JaegerTracer.live, Tracing.live, PrometheusClient.live, Console.live, ZioQuillContext.dataSourceLayer, OptionsRepository.layer, ValidationsRepository.layer, FormElementsRepository.layer, logLayer, AsyncHttpClientZioBackend.layer(), UserService.layerKeycloakService, FormRepository.layer, FormService.layer, system.System.live) @@ aspServerStartCountAll
      _ <- putStrLn(s"Shutting down the server at port, $backendPort")
    } yield s
  }

  val diagnosticsLayer: ZLayer[ZEnv, Throwable, Has[Diagnostics]] =
    Diagnostics.make("localhost", 1111)

  val runtime: Runtime[ZEnv] =
    Runtime.default.mapPlatform(_.withSupervisor(ZMXSupervisor))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    runtime.unsafeRun(program.provideCustomLayer(diagnosticsLayer ++ configLayer))
    //program.exitCode
  }
}
