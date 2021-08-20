package com.bootes.client

import com.bootes.client.ZSttpClient.getRateLimiter
import com.bootes.config.Configuration.keycloakConfigValue
import com.bootes.dao.User
import com.bootes.dao.keycloak.Models.{ApiResponseError, ApiResponseSuccess, KeycloakUser, ServiceContext}
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
  def get[T <: (Product with Serializable), U <: (Product with Serializable)](url: String, inputRequest: T, successType: Class[U], formType: FormType = FormUsingJson)(implicit serviceContext: ServiceContext, encoder: JsonEncoder[T], decoder: JsonDecoder[U], printRecordsNo: Int = 1): ZIO[SttpClient with Logging, Serializable, Either[String, U]] = {
    for {
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
        log.debug(s"GET: $url")
      )
      res          <- {
        import sttp.client3._
        import scala.concurrent.duration._
        val payload = inputRequest.toJson
        log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(payload)))(
          log.debug(s"GET $url")
        )
        val req = serviceContext.token match {
          case "" =>
            basicRequest.get(uri"$url").readTimeout(serviceContext.readTimeout)
          case _ =>
            basicRequest.auth.bearer(serviceContext.token).get(uri"$url").readTimeout(serviceContext.readTimeout)
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
                  val xs = data.fromJson[U]
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(if (xs.isLeft) xs.left.toOption.getOrElse("Error in getting error projection of the response") else "JSON fetched and prepared from the response")))(
                    log.debug(s"Received response for $url")
                  ) &> {
                    ZIO.succeed(xs)
                  }
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

  def getCollection[T <: (Product with Serializable), U <: (Product with Serializable)](url: String, inputRequest: T, successType: Class[List[U]], formType: FormType)(implicit serviceContext: ServiceContext, encoder: JsonEncoder[T], decoder: JsonDecoder[U], printRecordsNo: Int = 1): ZIO[SttpClient with Logging with Clock, Serializable, Either[String, List[U]]] = {
    for {
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
        log.debug(s"GET: Getting collection from $url")
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
            basicRequest.get(uri"$url").readTimeout(serviceContext.readTimeout)
          case _ =>
            basicRequest.auth.bearer(serviceContext.token).get(uri"$url").readTimeout(serviceContext.readTimeout)
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
                  val xs = data.fromJson[List[U]]
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(if (xs.isLeft) xs.left.toOption.getOrElse("Error in getting error projection of the response") else "JSON prepared from the collection response")))(
                    log.debug(s"Received response for $url")
                  ) &> {
                    ZIO.succeed(xs)
                  }
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
                    basicRequest.body(payload, "utf-8").post(uri"$url").readTimeout(serviceContext.readTimeout)
                  case _ =>
                    basicRequest.auth.bearer(serviceContext.token).body(payload, "utf-8").post(uri"$url").readTimeout(serviceContext.readTimeout)
                }
              case FormUsingJson =>
                val payload = inputRequest.toJson
                log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(payload)))(
                  log.debug(s"POST $url as $formType")
                )
                basicRequest.contentType("application/json").auth.bearer(serviceContext.token).body(payload).post(uri"$url")
            }
            //val res: Task[Response[Either[String, String]]] = req.send(sttp.client3.logging.slf4j.Slf4jLoggingBackend(delegate = backend, logRequestHeaders = false, sensitiveHeaders = Set("Authorization")))
            val res: Task[Response[Either[String, String]]] = req.send(sttp.client3.logging.scribe.ScribeLoggingBackend(delegate = backend, logRequestHeaders = false, sensitiveHeaders = Set("Authorization")))
            res.flatMap(r => {
              r.body match {
                case Right(data) =>
                  val xs = data.fromJson[U]
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(if (xs.isLeft) xs.left.toOption.getOrElse("Error in getting error projection of the response") else "JSON prepared from the response")))(
                      log.debug(s"Received response and parsing successful for $url")
                  ) &> {
                    //println(s"DATA = $xs")
                    ZIO.succeed(xs)
                  }
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

  val login: ZIO[EventLoopGroup with ChannelFactory, Throwable, String] = {
    val token: String                                      = ""
    val headers                                            = List(Header.authorization(s"Bearer $token"))
    val loginRequest: ApiLoginRequest                         = ApiLoginRequest.default
    val loginUrl: String = "http://localhost:8180/auth/realms/master/protocol/openid-connect/token"
    val usersUrl: String = "http://localhost:8180/auth/admin/realms/bootes/users"
    val userCreateUrl: String = "http://localhost:8180/auth/admin/realms/bootes/users"
    for {
      login <- ZIO.fromEither(URL.fromString(loginUrl))
      loginByteBuf <- {
        ByteBuf.fromString(loginRequest.toJson, Charset.forName("utf-8"))
      }
      loginRequest = Request(endpoint = (Method.POST -> login), headers = List(zhttp.http.Header.contentTypeFormUrlEncoded), content = HttpData.fromByteBuf(loginByteBuf.asJava))
      res <- {
        println(s"URL = ${loginRequest.url}")
        //println(loginRequest.headers.toString())
        println(s"ZIO http request = ${loginRequest.getBodyAsString}")
        Client.request(loginRequest)
      }
      token = res.content match {
        case CompleteData(data) => {
          //println("token")
          //println(data.map(_.toChar).mkString)
          val result = data.map(_.toChar).mkString
          println(s"ZIO http response = ${result}")
          result
        }
        case _ => "<you shouldn't see this>"
      }
    } yield token
  }

  val getRateLimiter:zio.ZManaged[Clock, Nothing, RateLimiter] = {
    import zio.duration._
    import nl.vroste.rezilience._
    val rateLimiter: zio.ZManaged[Clock, Nothing, RateLimiter] = RateLimiter.make(max = 10, interval = 1.second)
    rateLimiter
  }

  def login(url: String, inputLoginRequest: Option[ApiLoginRequest])(implicit serviceContext: ServiceContext): ZIO[Logging with Clock, Serializable, Either[String, ApiToken]] = {
    val currentLoginRequest =  inputLoginRequest.getOrElse(ApiLoginRequest.default)
    post(url, currentLoginRequest, classOf[ApiToken], FormUrlEncoded)
  }

  val program: ZIO[Logging with Clock with Console with EventLoopGroup with ChannelFactory with system.System, Serializable, Unit] =
    for {
      configValue <- keycloakConfigValue
      url = s"${configValue.keycloak.url}/realms/${configValue.keycloak.masterRealm}/protocol/openid-connect/token"
      response <- {
        implicit val serviceContext = ServiceContext("", requestId = ServiceContext.newRequestId())
        login(url, Some(ApiLoginRequest.default))
      }
      //users         <- login >>= getUsers
      _             <- putStrLn(s"Found login response:\n\t${response}\n")
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.provideLayer(ZEnv.live ++ env ++ logEnv).exitCode
}
