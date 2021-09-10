package com.bootes.server

import com.bootes.config.config.AppConfig
import com.bootes.tracking.JaegerTracer
import zio.console.putStrLn
import zio.magic._
import zio.config.getConfig
import zio.config.typesafe.TypesafeConfig
import zio.config.magnolia.{Descriptor, descriptor}
import zio.telemetry.opentelemetry.Tracing
import zio.{App, Managed, ZIO, ZLayer}
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.model.Uri
import zhttp.service.{EventLoopGroup, Server}
import zhttp.service.server.ServerChannelFactory
import com.bootes.client.http.Client

object ProxyServer extends App {
  implicit val sttpUriDescriptor: Descriptor[Uri] =
    Descriptor[String].transformOrFailLeft(Uri.parse)(_.toString)

  val server =
    getConfig[AppConfig].flatMap { conf =>
      val port = conf.proxy.host.port.getOrElse(9090)
      (Server.port(port) ++ Server.app(ProxyApp.routes)).make.use(_ =>
        putStrLn(s"ProxyServer started on port $port") *> ZIO.never
      )
    }

  val configLayer = TypesafeConfig.fromDefaultLoader(descriptor[AppConfig])
  val httpBackend = ZLayer.fromManaged(Managed.make(AsyncHttpClientZioBackend())(_.close().ignore))

  override def run(args: List[String]) =
    server
      .injectCustom(
        configLayer,
        httpBackend,
        Client.live,
        JaegerTracer.live,
        Tracing.live,
        ServerChannelFactory.auto,
        EventLoopGroup.auto(0)
      )
      .exitCode
}
