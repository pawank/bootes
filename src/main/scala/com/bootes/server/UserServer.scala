package com.bootes.server

import com.bootes.dao.{UserService, ZioQuillContext}
import com.bootes.dao.{UserService, ZioQuillContext}
import com.bootes.dao.repository.{UserRepository, NotFoundException}
import com.bootes.server.auth.AuthenticationApp
import zhttp.http._
import zhttp.service.Server
import zio._
import zio.console._
import zio.magic._

object UserServer extends App {

  val endpoints: Http[Has[UserService] with Console, HttpError, Request, Response[Has[UserService] with Console, HttpError]] =
    AuthenticationApp.login +++ CORS(
      AuthenticationApp.authenticate(HttpApp.forbidden("None shall pass."), UserEndpoints.user),
      config = CORSConfig(anyOrigin = true)
    ) +++ InvoiceEndpoints.invoiceRoutes

  val program: ZIO[Any, Throwable, Nothing] = {
    import sttp.client3._
    import sttp.client3.asynchttpclient.zio._
    Server
      .start(8080, endpoints)
      .inject(Console.live, AsyncHttpClientZioBackend.layer(), UserService.layerKeycloakService)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode
}
