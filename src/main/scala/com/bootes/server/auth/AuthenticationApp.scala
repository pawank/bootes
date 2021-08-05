package com.bootes.server.auth

import com.bootes.server.RequestOps
import com.bootes.server.RequestOps
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zhttp.http._
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.{IO, ZIO}

import java.time.Clock

object AuthenticationApp extends RequestOps {
  // Secret Authentication key
  val SECRET_KEY = "secretKey"

  implicit val clock: Clock = Clock.systemUTC

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

  // Authentication middleware
  // Takes in a Failing HttpApp and a Succeed HttpApp which are called based on Authentication success or failure
  // For each request tries to read the `X-ACCESS-TOKEN` header
  // Validates JWT Claim
  def authenticate[R, E](fail: HttpApp[R, E], success: JwtClaim => HttpApp[R, E]): HttpApp[R, E] = Http.flatten {
    Http
      .fromFunction[Request] {
        _.getHeader("Authorization")
          .flatMap(header => jwtDecode(header.value.toString))
          .fold[HttpApp[R, E]](fail)(success)
      }
  }

  def login: Http[Any, HttpError.Unauthorized, Request, UResponse] = Http
    .collectM[Request] { case req @ Method.POST -> Root / "bootes" / "v1" / "login" =>
      for {
        loginRequest      <- extractBodyFromJson[LoginRequest](req)
        authenticatedUser <- validateLogin(loginRequest)
        jwtClaim          <- ZIO.effect(jwtEncode(authenticatedUser.username))
      } yield Response.text(jwtClaim)
    }
    .catchAll { case FailedLogin(user) =>
      Http.fail(HttpError.Unauthorized(s"Failed login for user: $user."))
    }

  def validateLogin(request: LoginRequest): IO[FailedLogin, AuthenticatedUser] =
    if (request.password == request.username.reverse) {
      ZIO.succeed(AuthenticatedUser(request.username))
    } else {
      ZIO.fail(FailedLogin(request.username))
    }
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
  val requestedUsername = System.getenv("REQUESTED_USERNAME")
  val requestedPassword = System.getenv("REQUESTED_PASSWORD")
  val default = ApiLoginRequest(scope = "openid", client_id = "rapidor-jwt", client_secret = clientSecret, grant_type = "password", username = requestedUsername, password = requestedPassword)
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
