package com.bootes.dao

import com.bootes.dao.keycloak.Models.ServiceContext
import com.bootes.dao.repository.UserRepository
import zio._
import zio.console._
import zio.magic._

object ZioQuillRepositoryExample extends App {
  implicit val serviceContext = ServiceContext("", requestId = ServiceContext.newRequestId())

  val items = Seq(CreateUserRequest.sample)

  val startup: ZIO[Has[UserService], Throwable, Seq[User]] = ZIO.foreachPar(items)(UserService.create)

  val program: ZIO[Console with Has[UserService], Throwable, Seq[User]] =
    (startup *> UserService.all.tap(a => putStrLn(s"Found: \n\t${a.mkString("\n\t")}")))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.inject(Console.live, ZioQuillContext.dataSourceLayer, UserService.layer, UserRepository.layer).exitCode
}
