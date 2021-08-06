package com.bootes.server

import com.bootes.dao.keycloak.Models.ServiceContext
import com.bootes.dao.repository.NotFoundException
import com.bootes.dao.{CreateUserRequest, ResponseMessage, UserService}
import com.bootes.dao.{CreateUserRequest, UserService}
import com.bootes.server.auth.Token
import pdi.jwt.JwtClaim
import zhttp.http._
import zio.console._
import zio.json._
import zio.{Has, IO, ZIO}

object UserEndpoints extends RequestOps {

  //val user: JwtClaim => Http[Has[UserService] with Console, HttpError, Request, UResponse] = jwtClaim =>
  val user: Token => Http[Has[UserService] with Console, HttpError, Request, UResponse] = jwtClaim => {
    println(s"Claim = $jwtClaim")
    implicit val serviceContext: ServiceContext = ServiceContext(token = jwtClaim.value)
    Http
      .collectM[Request] {
        case Method.GET -> Root / "bootes" / "v1" / "users" =>
          for {
            _ <- putStrLn(s"Validated claim: $jwtClaim")
            users <- UserService.all
          } yield Response.jsonString(users.toJson)
        case Method.GET -> Root / "bootes" / "v1" / "users" / id =>
          for {
            _ <- putStrLn(s"Validated claim: $jwtClaim")
            user <- UserService.get(id.toInt)
          } yield Response.jsonString(user.toJson)
        case req@Method.POST -> Root / "bootes" / "v1" / "users" =>
          for {
            _ <- putStrLn(s"Validated claim: $jwtClaim")
            request <- extractBodyFromJson[CreateUserRequest](req)
            results <- UserService.create(request)
          } yield Response.jsonString(results.toJson)
      }
      .catchAll {
        case NotFoundException(msg, id) =>
          Http.fail(HttpError.NotFound(Root / "bootes" / "v1" / "users" / id.toString))
        case ex: Throwable =>
          Http.fail(HttpError.InternalServerError(msg = ex.getMessage, cause = None))
          //Http.fail(HttpError.InternalServerError(msg = ex.getMessage, cause = Option(ex)))
        //val error = ResponseMessage(status = false, code = 501, message = ex.getMessage, details = Option(ex.getStackTrace.mkString))
        //Http.succeed(Response.jsonString(error.toJson))
        case err => Http.fail(HttpError.InternalServerError(msg = err.toString))
      }
  }
}

trait RequestOps {

  def extractBodyFromJson[A](request: Request)(implicit codec: JsonCodec[A]): IO[Serializable, A] =
    for {
      requestOrError <- ZIO.fromOption(request.getBodyAsString.map(_.fromJson[A]))
      body           <- ZIO.fromEither(requestOrError)
    } yield body
}
