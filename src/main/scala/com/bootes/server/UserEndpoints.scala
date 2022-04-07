package com.bootes.server

import com.bootes.dao.keycloak.Models.{KeyValue, QueryParams, RequestParsingError, ServiceContext, UserAlreadyExists, UserDoesNotExists}
import com.bootes.dao.repository.NotFoundException
import com.bootes.dao.{CreateUserRequest, ResponseMessage, UserService}
import com.bootes.dao.{CreateUserRequest, UserService}
import com.bootes.server.UserServer.{CorrelationId, DebugJsonLog}
import com.bootes.server.auth.{ApiToken, FailedLogin, LogoutRequest, Token}
import pdi.jwt.JwtClaim
import zhttp.http._
import zio.console._
import zio.duration.durationInt
import zio.json._
import zio.{Has, IO, UIO, ZIO}
import zio.logging._
import zio.logging.slf4j._
import zio.telemetry.opentelemetry.Tracing

import java.util.UUID

object UserEndpoints extends RequestOps {

  val user: ApiToken => Http[Has[UserService] with Console with Logging with zemail.email.Email, HttpError, Request, UResponse] = jwtClaim => {
    scribe.debug(s"Claim found for ${jwtClaim.name}")
    Http
      .collectM[Request] {
        case req @ Method.GET -> Root / "bootes" / "v1" / "users" =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            //_ <- ZIO.succeed(scribe.info("Getting list of all users"))
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.debug("Calling user service for fetching all users matching with criteria")
            )
            request <- {
              ZIO.effect({
                val params = req.url.queryParams
                QueryParams(params.keySet.map(key => KeyValue(key, params.get(key).getOrElse(List.empty).head)))
              })
            }
            users <- {
              UserService.all(if (request.isFound) Some(request) else None)
            }
          } yield Response.jsonString(users.toJson)
        case Method.GET -> Root / "bootes" / "v1" / "users" / id =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            user <- UserService.get(UUID.fromString(id))
          } yield Response.jsonString(user.toJson)
        case req @ Method.PUT -> Root / "bootes" / "v1" / "users" / id =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            request <- extractBodyFromJson[CreateUserRequest](req)
            user <- UserService.update(UUID.fromString(id), request)
          } yield Response.jsonString(user.toJson)
        case req @ Method.DELETE -> Root / "bootes" / "v1" / "users" / id =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            user <- UserService.delete(UUID.fromString(id))
          } yield Response.jsonString(user.toJson)
        case req@Method.POST -> Root / "bootes" / "v1" / "users" =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            request <- extractBodyFromJson[CreateUserRequest](req)
            results <- UserService.upsert(request, methodType = Some("post"))(serviceContext.copy(requestId = request.requestId.map(UUID.fromString(_)).getOrElse(serviceContext.requestId)))
            notify <- {
              val email = results.contactMethod.map(_.email1.getOrElse("")).getOrElse("")
              if (email.trim().isEmpty()) {
                ZIO.succeed(s"No email to be sent because email found is empty for user, $request")
              } else {

              val url = "https://forms.rapidor.co"
              val body = com.bootes.utils.EmailUtils.welcomeTemplate.replaceAll("___FULLNAME___", results.name.firstName).replaceAll("___DOMAIN___", url)
              com.bootes.utils.EmailUtils.send("Customer Care <admin@rapidor.co>", email, "Welcome", body)
              }
            }
          } yield Response.jsonString(results.toJson)
        case req@Method.POST -> Root / "bootes" / "v1" / "users" / "logout" =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            request <- extractBodyFromJson[Token](req)
            results <- {
              val r = LogoutRequest.makeRequest(request)
              UserService.logout("", r)
            }
          } yield Response.jsonString(results.toJson)
      }
      .catchAll {
        case UserDoesNotExists(msg) =>
          Http.fail(HttpError.BadRequest(msg))
        case UserAlreadyExists(msg) =>
          Http.fail(HttpError.Conflict(msg))
        case RequestParsingError(msg) =>
          Http.fail(HttpError.BadRequest(msg))
        case NotFoundException(msg, id) =>
          Http.fail(HttpError.NotFound(Root / "bootes" / "v1" / "users" / id.toString))
        case ex: Throwable =>
          val error = ex.getMessage
          val finalError = if (error.contains("(missing)")) {
            val tokens = error.replaceAll("""\(missing\)""","").split("""\.""")
            if (tokens.size >= 2) s"""${tokens(1)} is missing""" else tokens(0)
          } else error
          println(s"User Exception: $finalError")
          Http.fail(HttpError.InternalServerError(msg = finalError, cause = None))
        case err =>
          val error = err.toString
          println(error)
          if (error.contains("(missing)")) {
            val tokens = error.replaceAll("""\(missing\)""","")
            val finalError = s"""${tokens} is missing"""
            println(s"User ERROR: $finalError")
            Http.fail(HttpError.BadRequest(msg = finalError))
          } else {
            Http.fail(HttpError.InternalServerError(msg = error))
          }
      }
  }
}

trait RequestOps {
  def getServiceContext(jwtClaim: ApiToken): ServiceContext = ServiceContext(token = jwtClaim.access_token.getOrElse(""), requestId = UUID.fromString(jwtClaim.requestId.getOrElse("")))

  def extractBodyFromJson[A](request: Request)(implicit codec: JsonCodec[A], serviceContext: ServiceContext): ZIO[Logging, Serializable, A] =
    for {
      requestOrError <- ZIO.fromOption(request.getBodyAsString.map(_.fromJson[A]))
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
        if (requestOrError.isRight) log.info(s"Request body is successfully parsed to the type") else log.info(s"Request body parsing error: ${requestOrError.left.toSeq.mkString}")
      )
      body           <- {
        ZIO.fromEither(requestOrError).mapError(e => RequestParsingError(s"Request body parsing error: ${e}"))
      }
    } yield body
}
