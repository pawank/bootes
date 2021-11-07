package com.bootes.server.auth

import com.bootes.client.{FormUrlEncoded, ZSttpClient}
import com.bootes.config.Configuration.{KeycloakConfig, keycloakConfigValue}
import com.bootes.dao.Metadata
import com.bootes.dao.keycloak.Models.ServiceContext
import com.bootes.server.UserServer.{CorrelationId, DebugJsonLog}
import com.bootes.server.{RequestOps, UserServer}
import com.bootes.server.auth.ApiLoginRequest.{clientId, clientSecret, requestedPassword, requestedUsername}
import com.bootes.server.auth.keycloak.KeycloakClientExample
import io.netty.buffer.Unpooled
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtOptions}
import zhttp.core.ByteBuf
import zhttp.http._
import zio.clock.Clock
import zio.json.{DeriveJsonCodec, EncoderOps, JsonCodec}
import zio.logging.{Logging, log}
import zio.zmx.metrics.{MetricAspect, MetricsSyntax}
import zio.{IO, ZIO, system}

import java.util.UUID
import scala.runtime.Nothing$

case class Token(value: String) extends AnyVal
object Token {
  implicit val codec: JsonCodec[Token] = DeriveJsonCodec.gen[Token]
}

case class FailedLogin(user: String, message: String, code: Int = 400)


case class AuthenticatedUser(username: String, accessToken: String, refreshToken: String)
object AuthenticatedUser {
  implicit val codec: JsonCodec[AuthenticatedUser] = DeriveJsonCodec.gen[AuthenticatedUser]
}

case class ApiLoginRequest(scope: String, client_id: String, client_secret: String, grant_type: String, username: String, password: String) {
  val isAdminCli = client_id.equalsIgnoreCase("admin-cli")
}

object ApiLoginRequest {
  implicit val codec: JsonCodec[ApiLoginRequest] = DeriveJsonCodec.gen[ApiLoginRequest]
  val clientSecret = System.getenv("CLIENT_SECRET")
  val clientId = System.getenv("CLIENT_ID")
  val requestedUsername = System.getenv("REQUESTED_USERNAME")
  val requestedPassword = System.getenv("REQUESTED_PASSWORD")
  val default = ApiLoginRequest(scope = "openid", client_id = clientId, client_secret = clientSecret, grant_type = "password", username = requestedUsername, password = requestedPassword)
  def getLoginRequest(clientId: String): ApiLoginRequest = clientId.equalsIgnoreCase("admin-cli") match {
    case true => default
    case _ => default.copy(client_id = TokenValidationRequest.appClientId, client_secret = TokenValidationRequest.appClientSecret)
  }
}

case class TokenValidationRequest(token_type_hint: String = "access_token", client_id: String, client_secret: String, token: String)
object TokenValidationRequest {
  implicit val codec: JsonCodec[TokenValidationRequest] = DeriveJsonCodec.gen[TokenValidationRequest]
  val appClientSecret = System.getenv("APP_CLIENT_SECRET")
  val appClientId = System.getenv("APP_CLIENT_ID")
  def makeRequest(token: Token) = TokenValidationRequest(client_id = appClientId, client_secret = appClientSecret, token = token.value)
  def makeAdminRequest(token: Token) = TokenValidationRequest(client_id = clientId, client_secret = clientSecret, token = token.value)
}

case class LogoutRequest(client_id: String, client_secret: String, refresh_token: String)
object LogoutRequest {
  implicit val codec: JsonCodec[LogoutRequest] = DeriveJsonCodec.gen[LogoutRequest]
  val appClientSecret = System.getenv("APP_CLIENT_SECRET")
  val appClientId = System.getenv("APP_CLIENT_ID")
  def makeRequest(token: Token) = LogoutRequest(client_id = appClientId, client_secret = appClientSecret, refresh_token= token.value)
  def makeAdminRequest(token: Token) = LogoutRequest(client_id = clientId, client_secret = clientSecret, refresh_token= token.value)
}


case class LoginRequest(username: String, password: String, clientId: Option[String] = None) {
  val isAdminCli = clientId.getOrElse("").equalsIgnoreCase("admin-cli")
}

object LoginRequest {
  implicit val codec: JsonCodec[LoginRequest] = DeriveJsonCodec.gen[LoginRequest]

  val adminLoginRequest = LoginRequest(username = ApiLoginRequest.requestedUsername, password = ApiLoginRequest.requestedPassword, clientId = Some("admin-cli"))
}

case class ApiToken (
                      access_token: Option[String],
                      expires_in: Option[Int],
                      exp: Option[Int],
                      jti: Option[String],
                      refresh_expires_in: Option[Int],
                      refresh_token: Option[String],
                      token_type: Option[String],
                      id_token: Option[String],
                      client_id: Option[String],
                      session_state: Option[String],
                      scope: Option[String],
                      active: Option[Boolean],
                      name: Option[String],
                      email: Option[String],
                      username: Option[String],
                      requestId: Option[String]
                    ) {
  override def toString(): String = s"ApiToken(username=$username, scope=$scope, name=$name, client_id=$client_id, requestId=$requestId, active=$active)"
}
object ApiToken{
  implicit val codec: JsonCodec[ApiToken] = DeriveJsonCodec.gen[ApiToken]
}

object AuthenticationApp extends RequestOps {
  // Secret Authentication key
  val SECRET_KEY = "secretKey"

  implicit val clock: java.time.Clock = java.time.Clock.systemUTC

  // Helper to encode the JWT token
  def jwtEncode(username: String): String = {
    val json  = s"""{"user": "${username}"}"""
    val claim = JwtClaim { json }.issuedNow.expiresIn(300)
    Jwt.encode(claim, SECRET_KEY, JwtAlgorithm.HS512)
  }

  // Helper to decode the JWT token
  // Should be: Bearer <JWT HERE>
  def jwtDecode(header: String): Option[JwtClaim] = {
    if (header.startsWith("Bearer")) {
      for {
        token <- header.split(" ").lastOption
        claim <- Jwt.decode(token, SECRET_KEY, Seq(JwtAlgorithm.HS512)).toOption
      } yield claim
    } else {
      Option.empty
    }
  }

  def getMaybeToken(header: String): Option[String] = {
    if (header.startsWith("Bearer")) {
      for {
        token <- header.split(" ").lastOption
      } yield token
    } else {
      Option.empty
    }
  }

  def makeToken(token: String): String = token

  def getToken(header: String)(implicit serviceContext: ServiceContext): Option[ApiToken] = {
    val result:ZIO[Logging with Clock with system.System, Nothing, Option[ApiToken]] = (for {
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
        log.debug(s"Received token for checking validity")
      )
      //token: ZIO[Any, String, Token] <- {
        token <- {
        if (!header.isEmpty) {
          ZIO.succeed(Token(header))
        } else ZIO.fail(s"Bearer token, $header is invalid")
      }
      tt <- {
        getApiToken(token)
      }
      adminTT <- {
        if (tt.active.getOrElse(false)) {
          log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(tt.name.getOrElse(""))))(
            log.debug(s"Received valid token and now fetching admin token")
          ) &> performLogin(LoginRequest.adminLoginRequest)
        } else ZIO.fail(s"Admin token has expired")
      }
      r <- {
        val isActive = adminTT.access_token.getOrElse("").nonEmpty
        //val isActive = adminTT.map(_.active.getOrElse(false)).getOrElse(false)
        if (isActive) {
          log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(adminTT.name.getOrElse(""))))(
            log.debug(s"Received valid token for admin now")
          ) &> ZIO.succeed(Option(adminTT.copy(username = tt.username, email = tt.email, name = tt.name, client_id = tt.client_id)))
        } else ZIO.fail(s"Bearer token has expired for username, ${tt.username}")
        //if (adminTT.active.getOrElse(false)) ZIO.succeed(Option(adminTT)) else ZIO.fail(s"Bearer token has expired for username, ${tt.username}")
      }
    } yield r).catchAll(ex => {
      log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog("Please check input token")))(
        log.debug(s"Error while checking validity of the token, ${ex}")
      ) &>
      ZIO.succeed(None.asInstanceOf[Option[ApiToken]])
    })
    val r = result.provideLayer(UserServer.logLayer ++ Clock.live ++ system.System.live)
        val runtime = zio.Runtime.default
        runtime.unsafeRun(r)
  }

  // Authentication middleware
  // Takes in a Failing HttpApp and a Succeed HttpApp which are called based on Authentication success or failure
  // For each request tries to read the `X-ACCESS-TOKEN` header
  // Validates JWT Claim
  //def authenticate[R, E](fail: HttpApp[R, E], success: JwtClaim => HttpApp[R, E]): HttpApp[R, E] = Http.flatten {
  def authenticate[R, E](fail: HttpApp[R, E], success: ApiToken => HttpApp[R, E]): HttpApp[R, E] = Http.flatten {
      Http
        .fromFunction[Request] { req =>
          req.getHeader("Authorization")
            .flatMap(header => {
              implicit val serviceContext = ServiceContext(token = getMaybeToken(header.value.toString.trim).getOrElse(""), requestId = ServiceContext.newRequestId)
              val claim = jwtDecode(header.value.toString)
              //println(s"\n\n\nclaim = $claim")
              getToken(serviceContext.token)
            })
            //.flatMap(header => jwtDecode(header.value.toString))
            .fold[HttpApp[R, E]](fail)(success)
        }
  }

  def login: Http[Logging with Clock with system.System, HttpError.Unauthorized, Request, UResponse] = {
    Http
    .collectM[Request] { case req@Method.POST -> Root / "bootes" / "v1" / "login" =>
      implicit val serviceContext: ServiceContext = ServiceContext(token = "", requestId = ServiceContext.newRequestId())
      for {
        _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
          log.info("Extracting login payload for checking the credentials")
        )
        loginRequest <- extractBodyFromJson[LoginRequest](req) @@ MetricAspect.count("loginCounter")
        authenticatedUser <- {
          validateLogin(loginRequest)
        }
        jwtClaim <- {
          ZIO.effect(authenticatedUser.copy(username = loginRequest.username)) @@ MetricAspect.count("loginSuccessCounter")
        }
      } yield {
        //println(s"CLAIM: $jwtClaim")
        Response.jsonString(jwtClaim.toJson)
      }
    }
      .catchAll {
        case FailedLogin(user, message, code) =>
          //Http.fail(HttpError.Unauthorized(s"Failed login for user: $user."))
          val msg = s"Failed login for user: $user."
          Http.fromEffect(ZIO.succeed(Response.HttpResponse(Status.UNAUTHORIZED, List.empty, HttpData.fromByteBuf(Unpooled.wrappedBuffer(msg.getBytes)))) @@ MetricAspect.count("loginErrorCounter"))
        case ex@_ =>
          val error = ex.toString
          if (error.contains("(missing)")) {
            val tokens = error.replaceAll("""\(missing\)""","").split(""" \.""")
            val finalError = if (tokens.size >= 2) tokens(1) else tokens(0)
            Http.fail(HttpError.Unauthorized(s"Login Failed with error: ${finalError} missing."))
          }
          else {
            Http.fail(HttpError.Unauthorized(s"Login Failed with error: ${error}"))
          }
      }
  }

  def getLoginUrl(configValue: KeycloakConfig, request: LoginRequest): String = {
    request.clientId match {
      case Some(id) if id.equalsIgnoreCase("admin-cli") =>
        s"${configValue.keycloak.url}/realms/${configValue.keycloak.masterRealm}/protocol/openid-connect/token"
      case _ =>
        s"${configValue.keycloak.url}/realms/${configValue.keycloak.realm.getOrElse("")}/protocol/openid-connect/token"
    }
  }

  def performLogin(request: LoginRequest)(implicit serviceContext: ServiceContext): ZIO[Logging with Clock with system.System, FailedLogin, ApiToken] = {
    val apiLoginRequest = if (request.isAdminCli) ApiLoginRequest.getLoginRequest(request.clientId.getOrElse("")).copy(username = ApiLoginRequest.default.username, password = ApiLoginRequest.default.password) else ApiLoginRequest.getLoginRequest(request.clientId.getOrElse("")).copy(username = request.username, password = request.password)
    val r: ZIO[Logging with Clock with system.System, Object, ApiToken] = for {
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(request.username)))(
        log.info("Performing login credentials validation")
      )
      configValue <- keycloakConfigValue
      url = getLoginUrl(configValue, request)
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(request.username)))(
        log.info(s"Sending login detail to url, $url for auth check")
      )
      maybeToken <- {
        ZSttpClient.login(url, Some(apiLoginRequest)).mapError(e => {
          FailedLogin(request.username, s"${e.toString}")
        })
      }
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(request.username)))(
        log.info(s"Received login detail from url, $url for auth check")
      )
      value <- {
        maybeToken match {
          case Right(tokenObject) =>
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(request.username)))(
              log.info(s"Login successful at ${Metadata.default.createdAt}")
            ) &>
              ZIO.succeed(tokenObject.copy(requestId = Some(serviceContext.requestId.toString)))
          case Left(error) =>
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(request.username)))(
              log.info(s"Login failed: $error")
            ) &>
            ZIO.fail(FailedLogin(request.username, error))
        }
      }
    } yield value
    r.mapError(e => {
      val is401 = e.toString.contains("error") && e.toString.contains("invalid_grant")
      if (is401) FailedLogin(request.username, "Invalid Login", 401) else FailedLogin(request.username, e.toString)
    })
  }

  def validateLogin(request: LoginRequest)(implicit serviceContext: ServiceContext): ZIO[Logging with Clock with system.System, FailedLogin, AuthenticatedUser] = {
    performLogin(request).map(u => AuthenticatedUser(u.username.getOrElse(""), u.access_token.getOrElse(""), u.refresh_token.getOrElse("")))
  }

  def getTokenValidityCheckUrl(configValue: KeycloakConfig, isAdmin: Boolean): String = {
    if (isAdmin)
      s"${configValue.keycloak.url}/realms/${configValue.keycloak.masterRealm}/protocol/openid-connect/token/introspect"
      else
      s"${configValue.keycloak.url}/realms/${configValue.keycloak.realm.getOrElse("")}/protocol/openid-connect/token/introspect"
  }

  def getApiToken(inputToken: Token)(implicit serviceContext: ServiceContext): ZIO[Logging with Clock with system.System, FailedLogin, ApiToken] = {
    val token: Token = inputToken
    val request = TokenValidationRequest.makeRequest(token)
    val r: ZIO[Logging with Clock with system.System, Object, ApiToken] = for {
      configValue <- keycloakConfigValue
      url = getTokenValidityCheckUrl(configValue, false)
      maybeToken <- {
        ZSttpClient.postOrPut(methodType = "post", url, request, classOf[ApiToken], FormUrlEncoded).mapError(e => FailedLogin("", s"${e.toString}"))
      }
      value <- {
        maybeToken match {
          case Right(tokenObject) =>
            //println(s"Token in getApiToken = $tokenObject")
            ZIO.succeed(tokenObject.copy(access_token = Some(token.value), requestId = Some(serviceContext.requestId.toString)))
          case Left(error) =>
            ZIO.fail(FailedLogin("", error))
        }
      }
    } yield value
    r.mapError(e => FailedLogin("", s"${e.toString}"))
  }
}

