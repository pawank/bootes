package com.bootes.server.auth.keycloak

import com.bootes.dao.User
import com.bootes.server.auth.{ApiLoginRequest, ApiToken, LoginRequest}
import zhttp.core.ByteBuf
import zhttp.http.HttpData.CompleteData
import zhttp.http._
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._
import zio.console._
import zio.json._

import java.nio.charset.Charset

object KeycloakClientExample extends App {

  val env: TaskLayer[ChannelFactory with EventLoopGroup] = ChannelFactory.auto ++ EventLoopGroup.auto()
  val loginUrl: String                                   = "http://localhost:8180/auth/realms/rapidor/protocol/openid-connect/token"
  val usersUrl: String                                   = "http://localhost:8080/bootes/v1/users"
  val token: String                                      = ""
  val headers                                            = List(Header.authorization(s"Bearer $token"))
  val loginRequest: ApiLoginRequest                         = ApiLoginRequest.default

  val login: ZIO[EventLoopGroup with ChannelFactory, Throwable, String] =
    for {
      login        <- ZIO.fromEither(URL.fromString(loginUrl))
      loginByteBuf <- {
        ByteBuf.fromString(loginRequest.toJson, Charset.forName("utf-8"))
      }
      loginRequest  = Request(endpoint = (Method.POST -> login), headers = List(zhttp.http.Header.contentTypeFormUrlEncoded), content = HttpData.fromByteBuf(loginByteBuf.asJava))
      res          <- {
          println(s"URL = ${loginRequest.url}")
          //println(loginRequest.headers.toString())
          println(s"ZIO http request = ${loginRequest.getBodyAsString}")
          Client.request(loginRequest)
      }
      token         = res.content match {
        case CompleteData(data) => {
          //println("token")
          //println(data.map(_.toChar).mkString)
          val result = data.map(_.toChar).mkString
          println(s"ZIO http response = ${result}")
          result
        }
        case _                  => "<you shouldn't see this>"
      }
    } yield token

  def loginViaSttp(inputLoginRequest: Option[ApiLoginRequest]): ZIO[Any, Serializable, Either[String, ApiToken]] = {
    val currentLoginRequest =  inputLoginRequest.getOrElse(loginRequest)
    for {
      res          <- {
        import sttp.client3._
        import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
        AsyncHttpClientZioBackend.managed().use { backend =>
          val payload = com.bootes.utils.getCCParams(currentLoginRequest)
          println(s"Payload = $payload")
          val req = basicRequest.body(payload, "utf-8").post(uri"$loginUrl")
          println(s"Sending sttp request = $req")
          val res: Task[Response[Either[String, String]]] = req.send(backend)
          println(s"Response based on sttp = $res")
          res.flatMap(r => {
            r.body match {
              case Right(data) =>
                println(s"Sttp zio response = ${data}")
                ZIO.succeed(data.fromJson[ApiToken])
              case _ => ZIO.fail(Left("<you shouldn't see this>"))
            }
          })
        }
      }
    } yield res
  }

  def getUsers(maybeToken: Either[String, ApiToken]): ZIO[EventLoopGroup with ChannelFactory, Serializable, Seq[User]] = {
    maybeToken match {
      case Right(tokenObject) =>
        val token = tokenObject.access_token
        for {
          item  <- ZIO.fromEither(URL.fromString(usersUrl))
          res   <- {
            println(s"Got token = $token")
            Client.request(Request(endpoint = Method.GET -> item, headers = List(Header.authorization(s"Bearer $token"))))
          }
          users <- {
            ZIO.fromEither(res.content match {
              case CompleteData(data) => {
                println(data.map(_.toChar).mkString)
                data.map(_.toChar).mkString.fromJson[Seq[User]]
              }
              case _                  => Left("Unexpected data type")
            })
          }
        } yield users
      case Left(error) =>
        ZIO.effect(println(s"Token error = $error")) &> ZIO.succeed(Seq.empty)
    }
  }

  val program: ZIO[Console with EventLoopGroup with ChannelFactory, Serializable, Unit] =
    for {
      users         <- loginViaSttp(Some(loginRequest)) >>= getUsers
      //users         <- login >>= getUsers
      //namesAndPrices = users.map(i => i.code -> i.status)
      //_             <- putStrLn(s"Found users:\n\t${namesAndPrices.mkString("\n\t")}")
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.provideLayer(ZEnv.live ++ env).exitCode
}
