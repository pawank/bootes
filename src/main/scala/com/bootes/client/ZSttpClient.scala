package com.bootes.client

import com.bootes.client.ZSttpClient.getRateLimiter
import com.bootes.config.Configuration.keycloakConfigValue
import com.bootes.dao.keycloak.Models.{ApiResponseError, ApiResponseSuccess, KeycloakUser, QueryParams, ServiceContext}
import com.bootes.server.UserServer.{CorrelationId, DebugJsonLog, logEnv}
import com.bootes.server.auth.{ApiLoginRequest, ApiToken, LoginRequest}
import nl.vroste.rezilience.RateLimiter
import sttp.client3.{Response, basicRequest}
import sttp.client3.asynchttpclient.zio.{SttpClient, send}
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.Uri.QuerySegment
import sttp.model.{Uri, UriInterpolator}
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

case class NoContent(value: String)
object NoContent {
  implicit val codec: JsonCodec[NoContent] = DeriveJsonCodec.gen[NoContent]
}


trait HttpClient {
  def get[T <: (Product with Serializable), U <: (Product with Serializable)](url: String, inputRequest: T, successType: Class[U], formType: FormType = FormUsingJson, queryParams: Option[QueryParams] = None)(implicit serviceContext: ServiceContext, encoder: JsonEncoder[T], decoder: JsonDecoder[U], printRecordsNo: Int = 1): ZIO[SttpClient with Logging, Serializable, Either[String, U]] = {
    for {
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
        log.info(s"GET: $url")
      )
      res          <- {
        import sttp.client3._
        import scala.concurrent.duration._
        val payload = inputRequest.toJson
        val uri = uri"${url}?${QueryParams.makeUri(queryParams, false)}"
        log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(payload)))(
          log.info(s"GET $url and uri = $uri")
        )
        val req = serviceContext.token match {
          case "" => {
            basicRequest.get(uri).readTimeout(serviceContext.readTimeout)
          }
          case _ => {
            basicRequest.auth.bearer(serviceContext.token).get(uri).readTimeout(serviceContext.readTimeout)
          }
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
                  //println(s"GET data = $data")
                  val xs = data.fromJson[U]
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(if (xs.isLeft) xs.left.toOption.getOrElse("Error in getting error projection of the response") else "JSON fetched and prepared from the response")))(
                    log.info(s"${r.code} Received response for $url")
                  ) &> {
                    ZIO.succeed(xs)
                  }
                case Left(error) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.error(s"${r.code} Received error $error for $url")
                  ) &>
                    ZIO.fail(Left(error))
              }
            case false =>
              r.body match {
                case Right(data) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.info(s"${r.code} Received response $data for $url")
                  ) &>
                    ZIO.fail(data)
                case Left(error) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.error(s"${r.code} Received error $error for $url")
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
        log.info(s"GET: Getting collection from $url")
      )
      res          <- {
        import sttp.client3._
        import scala.concurrent.duration._
        val payload = inputRequest.toJson
        log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(payload)))(
          log.info(s"GET $url as $formType")
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
                    log.info(s"${r.code} Received response for $url")
                  ) &> {
                    ZIO.succeed(xs)
                  }
                case Left(error) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.error(s"${r.code} Received error $error for $url")
                  ) &>
                    ZIO.fail(Left(error))
              }
            case false =>
              r.body match {
                case Right(data) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.info(s"${r.code} Received response $data for $url")
                  ) &>
                    ZIO.fail(data)
                case Left(error) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.error(s"${r.code} Received error $error for $url")
                  ) &>
                    ZIO.fail(Left(error))
              }
          }
        })
      }
    } yield output
  }

  def postOrPut[T <: (Product with Serializable), U <: (Product with Serializable)](methodType: String, url: String, inputRequest: T, successType: Class[U], formType: FormType)(implicit serviceContext: ServiceContext, encoder: JsonEncoder[T], decoder: JsonDecoder[U]): ZIO[Logging with Clock, Serializable, Either[String, U]] = {
    for {
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
        log.info(s"postOrPut: $url with method type, $methodType")
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
                //println(s"payload = $payload for url = $url")
                log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(payload.mkString)))(
                  log.info(s"$url as $formType with method type, $methodType")
                )
                serviceContext.token match {
                  case "" =>
                    methodType match {
                      case "put" =>
                        basicRequest.body(payload, "utf-8").put(uri"$url").readTimeout(serviceContext.readTimeout)
                      case _ =>
                        basicRequest.body(payload, "utf-8").post(uri"$url").readTimeout(serviceContext.readTimeout)
                    }
                  case _ =>
                    methodType match {
                      case "put" =>
                        basicRequest.auth.bearer(serviceContext.token).body(payload, "utf-8").put(uri"$url").readTimeout(serviceContext.readTimeout)
                      case _ =>
                        basicRequest.auth.bearer(serviceContext.token).body(payload, "utf-8").post(uri"$url").readTimeout(serviceContext.readTimeout)
                    }
                }
              case FormUsingJson =>
                val payload = inputRequest.toJson
                //println(s"FormUsingJson payload = $payload for url = $url")
                log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(payload)))(
                  log.info(s"$url as $formType with method type, $methodType")
                )
                methodType match {
                  case "put" =>
                    basicRequest.contentType("application/json").auth.bearer(serviceContext.token).body(payload).put(uri"$url")
                  case _ =>
                    basicRequest.contentType("application/json").auth.bearer(serviceContext.token).body(payload).post(uri"$url")
                }
            }
            //val res: Task[Response[Either[String, String]]] = req.send(sttp.client3.logging.slf4j.Slf4jLoggingBackend(delegate = backend, logRequestHeaders = false, sensitiveHeaders = Set("Authorization")))
            val res: Task[Response[Either[String, String]]] = req.send(sttp.client3.logging.scribe.ScribeLoggingBackend(delegate = backend, logRequestHeaders = false, sensitiveHeaders = Set("Authorization")))
            res.flatMap(r => {
              val statusCode = r.code.code >= 200 && r.code.code < 300
              r.body match {
                case Right(data) =>
                  println(s"postOrPut: data = $data and code = ${r.code}")
                  val xs = r.code.code match {
                    case 201 =>
                      """{"message":"Created"}""".fromJson[U]
                    case 204 =>
                      """{"value":"No Content"}""".fromJson[U]
                    case _ =>
                      data.fromJson[U]
                  }
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(if (xs.isLeft) xs.left.toOption.getOrElse("Error in getting error projection of the response") else "JSON prepared from the response")))(
                      log.debug(s"${r.code} Received response and parsing successful for $url")
                  ) &> {
                    ZIO.succeed(xs)
                  }
                case Left(error) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.error(s"${r.code} Received error $error for $url")
                  ) &>
                  ZIO.fail(error)
              }
            })
          }
        }
      }
    } yield res
  }

  def delete[T <: (Product with Serializable), U <: (Product with Serializable)](url: String, successType: Class[U])(implicit serviceContext: ServiceContext, decoder: JsonDecoder[U]): ZIO[Logging with Clock, Serializable, Either[String, U]] = {
    for {
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
        log.info(s"Delete: $url")
      )
      res          <- {
        getRateLimiter.use { rateLimiter =>
          import sttp.client3._
          import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
          import scala.concurrent.duration._
          val options = SttpBackendOptions.connectionTimeout(1.minute)
          AsyncHttpClientZioBackend.managed(options = options).use { backend =>
                val req = serviceContext.token match {
                  case "" =>
                        basicRequest.delete(uri"$url").readTimeout(serviceContext.readTimeout)
                  case _ =>
                        basicRequest.auth.bearer(serviceContext.token).delete(uri"$url").readTimeout(serviceContext.readTimeout)
                }
            //val res: Task[Response[Either[String, String]]] = req.send(sttp.client3.logging.slf4j.Slf4jLoggingBackend(delegate = backend, logRequestHeaders = false, sensitiveHeaders = Set("Authorization")))
            val res: Task[Response[Either[String, String]]] = req.send(sttp.client3.logging.scribe.ScribeLoggingBackend(delegate = backend, logRequestHeaders = false, sensitiveHeaders = Set("Authorization")))
            res.flatMap(r => {
              val statusCode = r.code.code >= 200 && r.code.code < 300
              r.body match {
                case Right(data) =>
                  //println(s"DELETE data = $data")
                  val xs = r.code.code match {
                    case 201 =>
                      """{"message":"Created"}""".fromJson[U]
                    case 204 =>
                      """{"message":"Resource Deleted Successfully"}""".fromJson[U]
                    case _ =>
                      data.fromJson[U]
                  }
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(if (xs.isLeft) xs.left.toOption.getOrElse("Error in getting error projection of the response") else "JSON prepared from the response")))(
                    log.debug(s"${r.code} Received response and parsing successful for $url")
                  ) &> {
                    ZIO.succeed(xs)
                  }
                case Left(error) =>
                  log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(res.toString)))(
                    log.error(s"${r.code} Received error $error for $url")
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
    postOrPut("post", url, currentLoginRequest, classOf[ApiToken], FormUrlEncoded)
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
