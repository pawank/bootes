package com.bootes.server.auth

import com.bootes.server.RequestOps
import com.bootes.server.RequestOps
import com.bootes.server.auth.keycloak.KeycloakClientExample
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtOptions}
import zhttp.http._
import zio.clock.Clock
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.{IO, ZIO}

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

  def makeToken(token: String): String = token
  def getToken(header: String): Option[Token] = {
    println(s"Received header = $header")
    if (header.startsWith("Bearer")) {
      for {
        token <- header.split(" ").lastOption
      } yield {
        Token(token)
      }
    } else {
      Option.empty
    }
  }

  // Authentication middleware
  // Takes in a Failing HttpApp and a Succeed HttpApp which are called based on Authentication success or failure
  // For each request tries to read the `X-ACCESS-TOKEN` header
  // Validates JWT Claim
  //def authenticate[R, E](fail: HttpApp[R, E], success: JwtClaim => HttpApp[R, E]): HttpApp[R, E] = Http.flatten {
  def authenticate[R, E](fail: HttpApp[R, E], success: Token => HttpApp[R, E]): HttpApp[R, E] = Http.flatten {
      Http
        .fromFunction[Request] {
          _.getHeader("Authorization")
            .flatMap(header => getToken(header.value.toString))
            //.flatMap(header => jwtDecode(header.value.toString))
            .fold[HttpApp[R, E]](fail)(success)
        }
  }

  def login: Http[Clock, HttpError.Unauthorized, Request, UResponse] = Http
    .collectM[Request] { case req @ Method.POST -> Root / "bootes" / "v1" / "login" =>
      for {
        loginRequest      <- extractBodyFromJson[LoginRequest](req)
        authenticatedUser <- validateLogin(loginRequest)
        jwtClaim          <- ZIO.effect(authenticatedUser.username)
        //jwtClaim          <- ZIO.effect(jwtEncode(authenticatedUser.username))
      } yield Response.text(jwtClaim)
    }
    .catchAll { case FailedLogin(user) =>
      Http.fail(HttpError.Unauthorized(s"Failed login for user: $user."))
    }

  def validateLogin(request: LoginRequest): ZIO[Clock, FailedLogin, AuthenticatedUser] = {
    val apiLoginRequest = ApiLoginRequest.default.copy(username = request.username, password = request.password)
    /*
    if (request.password == request.username.reverse) {
      ZIO.succeed(AuthenticatedUser(request.username))
    } else {
      ZIO.fail(FailedLogin(request.username))
    }
     */
    val r: ZIO[Clock, FailedLogin, AuthenticatedUser] = for {
      maybeToken <- KeycloakClientExample.loginViaSttp(Some(apiLoginRequest)).mapError(e => FailedLogin("Cannot serialize the login response"))
      value <- {
        maybeToken match {
          case Right(tokenObject) =>
            val token = tokenObject.access_token
            ZIO.succeed(AuthenticatedUser(token))
          case Left(error) =>
            ZIO.fail(FailedLogin(error))
        }
      }
    } yield value
    r
  }
}

case class Token(value: String) extends AnyVal
object Token {
  implicit val codec: JsonCodec[Token] = DeriveJsonCodec.gen[Token]
}

case class FailedLogin(user: String)

case class LoginRequest(username: String, password: String)

object LoginRequest {
  implicit val codec: JsonCodec[LoginRequest] = DeriveJsonCodec.gen[LoginRequest]
}

case class AuthenticatedUser(username: String)

case class ApiLoginRequest(scope: String, client_id: String, client_secret: String, grant_type: String, username: String, password: String)

object ApiLoginRequest {
  implicit val codec: JsonCodec[ApiLoginRequest] = DeriveJsonCodec.gen[ApiLoginRequest]
  val clientSecret = System.getenv("CLIENT_SECRET")
  val clientId = System.getenv("CLIENT_ID")
  val requestedUsername = System.getenv("REQUESTED_USERNAME")
  val requestedPassword = System.getenv("REQUESTED_PASSWORD")
  val default = ApiLoginRequest(scope = "openid", client_id = clientId, client_secret = clientSecret, grant_type = "password", username = requestedUsername, password = requestedPassword)
}

case class ApiToken (
                           access_token: String,
                           expires_in: Int,
                           refresh_expires_in: Int,
                           refresh_token: String,
                           token_type: String,
                           id_token: String,
  session_state: String,
  scope: String
  )
object ApiToken{
  implicit val codec: JsonCodec[ApiToken] = DeriveJsonCodec.gen[ApiToken]
}
