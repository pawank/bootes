package com.bootes.client

import com.bootes.client.ZSttpClient.{getRateLimiter, loginRequest, loginUrl, userCreateUrl}
import com.bootes.dao.User
import com.bootes.dao.keycloak.Models.{KeycloakError, KeycloakSuccess, KeycloakUser, ServiceContext}
import com.bootes.server.UserServer.{CorrelationId, DebugJsonLog, logEnv}
import com.bootes.server.auth.{ApiLoginRequest, ApiToken, LoginRequest}
import nl.vroste.rezilience.RateLimiter
import sttp.client3.{Response, basicRequest}
import sttp.client3.asynchttpclient.zio.{SttpClient, send}
import sttp.client3.{SttpBackend, basicRequest}
import zhttp.core.ByteBuf
import zhttp.http.HttpData.CompleteData
import zhttp.http._
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._
import zio.clock.Clock
import zio.console._
import zio.json._
import zio.logging.{Logging, log}

import java.nio.charset.Charset
import java.util.UUID

sealed trait FormType
case object FormUrlEncoded extends FormType
case object FormUsingJson extends FormType

trait HttpClient {
  def get[T <: (Product with Serializable), U <: (Product with Serializable)](url: String, inputRequest: T, successType: Class[List[U]], formType: FormType)(implicit serviceContext: ServiceContext, encoder: JsonEncoder[T], decoder: JsonDecoder[U]): ZIO[SttpClient with Logging with Clock, Serializable, Either[String, List[U]]] = {
    for {
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
        log.debug(s"GET: $url")
      )
      res          <- {
        import sttp.client3._
        import scala.concurrent.duration._
        val payload = inputRequest.toJson
        log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(payload)))(
          log.debug(s"GET $url as $formType")
        )
        val req = serviceContext.token match {
          case "" =>
            basicRequest.get(uri"$url").readTimeout(5.minutes)
          case _ =>
            basicRequest.auth.bearer(serviceContext.token).get(uri"$url").readTimeout(5.minutes)
        }
        send(req)
      }
      output <- {
        Task{res}.flatMap(r => {
          val statusCode = r.code.code >= 200 && r.code.code < 300
          statusCode match {
            case true =>
              r.body match {
                case Right(data) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.debug(s"Received response $data for $url")
                  ) &>
                    ZIO.succeed(data.fromJson[List[U]])
                case Left(error) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.debug(s"Received error $error for $url")
                  ) &>
                    ZIO.fail(Left(error))
              }
            case false =>
              r.body match {
                case Right(data) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.debug(s"Received response $data for $url")
                  ) &>
                    ZIO.fail(data)
                case Left(error) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.debug(s"Received error $error for $url")
                  ) &>
                    ZIO.fail(Left(error))
              }
          }
        })
      }
    } yield output
  }

  def post[T <: (Product with Serializable), U <: (Product with Serializable)](url: String, inputRequest: T, successType: Class[U], formType: FormType)(implicit serviceContext: ServiceContext, encoder: JsonEncoder[T], decoder: JsonDecoder[U]): ZIO[Logging with Clock, Serializable, Either[String, U]] = {
    for {
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
        log.debug(s"POST: $url")
      )
      res          <- {
        getRateLimiter.use { rateLimiter =>
          import sttp.client3._
          import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
          import scala.concurrent.duration._
          val options = SttpBackendOptions.connectionTimeout(1.minute)
          AsyncHttpClientZioBackend.managed(options = options).use { backend =>
            val req = formType match {
              case FormUrlEncoded =>
                val payload = com.bootes.utils.getCCParams(inputRequest)
                log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(payload.mkString)))(
                  log.debug(s"POST $url as $formType")
                )
                serviceContext.token match {
                  case "" =>
                    basicRequest.body(payload, "utf-8").post(uri"$url").readTimeout(5.minutes)
                  case _ =>
                    basicRequest.auth.bearer(serviceContext.token).body(payload, "utf-8").post(uri"$url").readTimeout(5.minutes)
                }
              case FormUsingJson =>
                val payload = inputRequest.toJson
                log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(payload)))(
                  log.debug(s"POST $url as $formType")
                )
                basicRequest.contentType("application/json").auth.bearer(serviceContext.token).body(payload).post(uri"$url")
            }
            val res: Task[Response[Either[String, String]]] = req.send(backend)
            res.flatMap(r => {
              r.body match {
                case Right(data) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.debug(s"Received response $data for $url")
                  ) &>
                  ZIO.succeed(data.fromJson[U])
                case Left(error) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.debug(s"Received error $error for $url")
                  ) &>
                  ZIO.fail(Left(error))
              }
            })
          }
        }
      }
    } yield res
  }

}

object ZSttpClient extends App with HttpClient {

  val env: TaskLayer[ChannelFactory with EventLoopGroup] = ChannelFactory.auto ++ EventLoopGroup.auto()
  val loginUrl: String                                   = "http://localhost:8180/auth/realms/master/protocol/openid-connect/token"
  val usersUrl: String                                   = "http://localhost:8180/auth/admin/realms/bootes/users"
  val userCreateUrl: String                              = "http://localhost:8180/auth/admin/realms/bootes/users"
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

  val getRateLimiter:zio.ZManaged[Clock, Nothing, RateLimiter] = {
    import zio.duration._
    import nl.vroste.rezilience._
    val rateLimiter: zio.ZManaged[Clock, Nothing, RateLimiter] = RateLimiter.make(max = 10, interval = 1.second)
    rateLimiter
  }

  def loginViaSttp(inputLoginRequest: Option[ApiLoginRequest]): ZIO[Logging with Clock, Serializable, Either[String, ApiToken]] = {
    val currentLoginRequest =  inputLoginRequest.getOrElse(loginRequest)
    implicit val ctx = ServiceContext(token = "")
    post(loginUrl, currentLoginRequest, classOf[ApiToken], FormUrlEncoded)
  }

  def getUsers(maybeToken: Either[String, ApiToken]): ZIO[EventLoopGroup with ChannelFactory, Serializable, Seq[User]] = {
    maybeToken match {
      case Right(tokenObject) =>
        val token = tokenObject.access_token
        for {
          item  <- ZIO.fromEither(URL.fromString(usersUrl))
          createUserResponse <- {
              //val client = new KeycloakAdminClient
              println(s"Create a new user")
              //client.createUser()
              //client.listUsers()
              import sttp.client3._
              import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
              AsyncHttpClientZioBackend.managed().use { backend =>
                val payload = KeycloakUser(username = "pawan5", firstName = "pawan", lastName = "kumar", email = Some("pawan5@test.com")).toJson
                val req = basicRequest.contentType("application/json").auth.bearer(token).body(payload).post(uri"$userCreateUrl")
                println(s"Sending sttp create user request = $req")
                val res: Task[Response[Either[String, String]]] = req.send(backend)
                println(s"Response based on sttp create user = $res")
                res.flatMap(r => {
                  println(s"Status code = ${r.code}")
                  (r.code.code == 200) || (r.code.code == 201) match {
                    case true =>
                      r.body match {
                        case Right(data) =>
                          println(s"Sttp zio response for user create = ${data}")
                          ZIO.succeed(Right(KeycloakSuccess(message = "Created")))
                        case Left(error) =>
                          ZIO.fail(Left(s"<you shouldn't see this> $error"))
                      }
                    case _ =>
                      r.body match {
                        case Right(data) =>
                          val success: String = data.fromJson[KeycloakError].fold(s => s, c => c.errorMessage)
                          ZIO.fail(success)
                        case Left(data) =>
                          println(s"Sttp zio response for user create = ${data}")
                          val error: String = data.fromJson[KeycloakError].fold(s => s, c => c.errorMessage)
                          ZIO.fail(Left(s"${error}"))
                      }
                  }
                })
              }
          }
          res   <- {
            println(s"Got token = $token")
            //Client.request(Request(endpoint = Method.GET -> item, headers = List(Header.authorization(s"Bearer $token"))))
            import sttp.client3._
            import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
            AsyncHttpClientZioBackend.managed().use { backend =>
              val req = basicRequest.auth.bearer(token).get(uri"$usersUrl")
              println(s"Sending sttp users request = $req")
              val res: Task[Response[Either[String, String]]] = req.send(backend)
              res.flatMap(r => {
                r.body match {
                  case Right(data) =>
                    println(s"Sttp zio users response = ${data}")
                    ZIO.succeed(data.fromJson[List[KeycloakUser]])
                  case Left(error) =>
                    ZIO.fail(Left(s"$error"))
                }
              })
            }
          }
          users <- {
            ZIO.fromEither(res match {
              case Right(data) => {
                println("Listing users..")
                Right(data.map(User.fromKeycloakUser(_)))
              }
              case _                  => Left("Unexpected data type")
            })
          }
        } yield users
      case Left(error) =>
        ZIO.effect(println(s"Token error = $error")) &> ZIO.succeed(Seq.empty)
    }
  }

  val program: ZIO[Logging with Clock with Console with EventLoopGroup with ChannelFactory, Serializable, Unit] =
    for {
      users         <- loginViaSttp(Some(loginRequest)) >>= getUsers
      //users         <- login >>= getUsers
      //namesAndPrices = users.map(i => i.code -> i.status)
      //_             <- putStrLn(s"Found users:\n\t${namesAndPrices.mkString("\n\t")}")
      _             <- putStrLn(s"Found users:\n\t${users.mkString("\n\t")}")
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.provideLayer(ZEnv.live ++ env ++ logEnv).exitCode
}
