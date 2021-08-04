package com.bootes.server

import com.bootes.dao.User
import com.bootes.server.auth.LoginRequest
import zhttp.core.ByteBuf
import zhttp.http.HttpData.CompleteData
import zhttp.http._
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._
import zio.console._
import zio.json._

object ClientExample extends App {

  val env: TaskLayer[ChannelFactory with EventLoopGroup] = ChannelFactory.auto ++ EventLoopGroup.auto()
  val loginUrl: String                                   = "http://localhost:8080/bootes/v1/login"
  val usersUrl: String                                   = "http://localhost:8080/bootes/v1/users"
  val token: String                                      = ""
  val headers                                            = List(Header.authorization(s"Bearer $token"))
  val loginRequest: LoginRequest                         = LoginRequest("Foobar", "rabooF")

  val login: ZIO[EventLoopGroup with ChannelFactory, Throwable, String] =
    for {
      login        <- ZIO.fromEither(URL.fromString(loginUrl))
      loginByteBuf <- ByteBuf.fromString(loginRequest.toJson)
      loginRequest  = Request(endpoint = (Method.POST -> login), headers = List(Header("Content-Type", "application/json")), content = HttpData.fromByteBuf(loginByteBuf.asJava))
      res          <- {
        //println(loginRequest.url)
        //println(loginRequest.headers.toString())
        //println(loginRequest.getBodyAsString)
        Client.request(loginRequest)
      }
      token         = res.content match {
        case CompleteData(data) => {
          //println("token")
          //println(data.map(_.toChar).mkString)
          data.map(_.toChar).mkString
        }
        case _                  => "<you shouldn't see this>"
      }
    } yield token

  def getUsers(token: String): ZIO[EventLoopGroup with ChannelFactory, Serializable, Seq[User]] =
    for {
      item  <- ZIO.fromEither(URL.fromString(usersUrl))
      res   <- {
        //println(s"calling url = $item")
        Client.request(Request(endpoint = Method.GET -> item, headers = List(Header.authorization(s"Bearer $token"))))
      }
      users <- {
        ZIO.fromEither(res.content match {
          case CompleteData(data) => {
            //println(data.map(_.toChar).mkString)
            data.map(_.toChar).mkString.fromJson[Seq[User]]
          }
          case _                  => Left("Unexpected data type")
        })
      }
    } yield users

  val program: ZIO[Console with EventLoopGroup with ChannelFactory, Serializable, Unit] =
    for {
      users         <- login >>= getUsers
      namesAndPrices = users.map(i => i.code -> i.status)
      _             <- putStrLn(s"Found users:\n\t${namesAndPrices.mkString("\n\t")}")
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.provideLayer(ZEnv.live ++ env).exitCode
}
