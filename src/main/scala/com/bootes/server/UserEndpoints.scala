package com.bootes.server

import com.bootes.dao.keycloak.Models.ServiceContext
import com.bootes.dao.repository.NotFoundException
import com.bootes.dao.{CreateUserRequest, ResponseMessage, UserService}
import com.bootes.dao.{CreateUserRequest, UserService}
import com.bootes.server.UserServer.{CorrelationId, DebugJsonLog}
import com.bootes.server.auth.{ApiToken, LogoutRequest, Token}
import pdi.jwt.JwtClaim
import zhttp.http._
import zio.console._
import zio.duration.durationInt
import zio.json._
import zio.{Has, IO, UIO, ZIO}
import zio.logging._
import zio.logging.slf4j._

import java.util.UUID

object UserEndpoints extends RequestOps {
  import com.github.mvv.zilog.{Logging => ZLogging, Logger => ZLogger, log => zlog}
  implicit val logger: ZLogger = ZLogger[UserServer.type]

  val user: ApiToken => Http[Has[UserService] with Console with Logging with ZLogging, HttpError, Request, UResponse] = jwtClaim => {
    scribe.debug(s"Claim found for ${jwtClaim.name}")
    implicit val serviceContext: ServiceContext = ServiceContext(token = jwtClaim.access_token.getOrElse(""), requestId = UUID.fromString(jwtClaim.requestId.getOrElse("")))
    Http
      .collectM[Request] {
        case Method.GET -> Root / "bootes" / "v1" / "users" =>
          for {
            //_ <- ZIO.succeed(scribe.info("Getting list of all users"))
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.debug("Calling user service for fetching all users matching with criteria")
            )
            users <- UserService.all
          } yield Response.jsonString(users.toJson)
        case Method.GET -> Root / "bootes" / "v1" / "users" / id =>
          for {
            user <- UserService.get(id.toInt)
          } yield Response.jsonString(user.toJson)
        case req@Method.POST -> Root / "bootes" / "v1" / "users" =>
          for {
            request <- extractBodyFromJson[CreateUserRequest](req)
            results <- UserService.create(request)(serviceContext.copy(requestId = request.requestId.map(UUID.fromString(_)).getOrElse(serviceContext.requestId)))
          } yield Response.jsonString(results.toJson)
        case req@Method.POST -> Root / "bootes" / "v1" / "users" / "logout" =>
          for {
            request <- extractBodyFromJson[Token](req)
            results <- {
              val r = LogoutRequest.makeRequest(request)
              UserService.logout("", r)
            }
          } yield Response.jsonString(results.toJson)
      }
      .catchAll {
        case NotFoundException(msg, id) =>
          Http.fail(HttpError.NotFound(Root / "bootes" / "v1" / "users" / id.toString))
        case ex: Throwable =>
          Http.fail(HttpError.InternalServerError(msg = ex.getMessage, cause = None))
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
