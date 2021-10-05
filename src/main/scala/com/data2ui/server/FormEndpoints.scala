package com.data2ui.server

import com.bootes.dao.keycloak.Models.ServiceContext
import com.bootes.dao.repository.NotFoundException
import com.bootes.server.UserEndpoints.getServiceContext
import com.bootes.server.UserServer
import com.bootes.server.UserServer.{CorrelationId, DebugJsonLog}
import com.bootes.server.auth.{ApiToken, LogoutRequest, Token}
import com.data2ui.FormService
import com.data2ui.models.Models.{CreateFormRequest, UiResponse, UploadResponse}
import pdi.jwt.JwtClaim
import zhttp.http._
import zio.console._
import zio.duration.durationInt
import zio.json._
import zio.{Has, IO, Task, UIO, ZIO}
import zio.logging._
import zio.logging.slf4j._

import java.nio.file.{Files, Paths}
import java.util.UUID

object FormEndpoints extends RequestOps {
  val form: ApiToken => Http[Has[FormService] with Console with Logging, HttpError, Request, UResponse] = jwtClaim => {
    Http
      .collectM[Request] {
        case Method.GET -> Root / "columba" / "v1" / "forms" / "search" =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            //_ <- ZIO.succeed(scribe.info("Getting list of all forms"))
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.debug("Calling form service for fetching all forms matching with criteria")
            )
            forms <- FormService.all
          } yield Response.jsonString(forms.toJson)
        case Method.GET -> Root / "columba" / "v1" / "forms" / "template" / id =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            user <- {
              //println(s"Form ID = $id")
              FormService.getTemplateForm(UUID.fromString(id))
            }
          } yield Response.jsonString(user.toJson)
        case Method.GET -> Root / "columba" / "v1" / "forms" / id =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            user <- {
              //println(s"Form ID = $id")
              FormService.get(UUID.fromString(id))
            }
          } yield Response.jsonString(user.toJson)
        case req@Method.POST -> Root / "columba" / "v1" / "forms" =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            request <- extractBodyFromJson[CreateFormRequest](req)
            results <- {
              val validation = req.url.queryParams.get("validation") match {
                case Some(xs) =>
                  xs.contains("true") || xs.contains("True")
                case _ =>
                  false
              }
              val orderedReq = request.copy(sections = request.sections.map(s => s.copy(elements = s.makeElementsOrdered())))
              if (validation) {
                val validatedForm = CreateFormRequest.validate(orderedReq)
                if (validatedForm.hasErrors) Task.succeed(validatedForm) else FormService.upsert(validatedForm)(serviceContext.copy(requestId = request.requestId.getOrElse(serviceContext.requestId)))
              } else FormService.upsert(orderedReq)(serviceContext.copy(requestId = request.requestId.getOrElse(serviceContext.requestId)))
            }
          } yield Response.jsonString(results.toJson)
        case req@Method.POST -> Root / "columba" / "v1" / "forms" / sectionName / stepNo =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          for {
            request <- extractBodyFromJson[CreateFormRequest](req)
            results <- {
              val orderedReq = request.copy(sections = request.sections.map(s => s.copy(elements = s.makeElementsOrdered())))
              //println(s"Route sectionName = $sectionName")
              val validatedForm = CreateFormRequest.validate(orderedReq)
              if (validatedForm.hasErrors) Task.succeed(validatedForm) else FormService.submit(orderedReq, sectionName, stepNo.toInt)(serviceContext.copy(requestId = request.requestId.getOrElse(serviceContext.requestId)))
            }
          } yield Response.jsonString(results.toJson)
        case req@Method.POST -> Root / "columba" / "v1" / "upload" =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          val formId = (req.url.queryParams.get("formId") match {
            case Some(xs) =>
              xs.headOption
            case _ =>
              None
          }).getOrElse("unknown")
          val filename = (req.url.queryParams.get("filename") match {
            case Some(xs) =>
              xs.headOption
            case _ =>
              None
          }).getOrElse(s"${UUID.randomUUID().toString}.dat")
          for {
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.info(s"Uploading file for form, $formId")
            )
            results <- {
              import better.files._
              import File._
              import java.io.{File => JFile}
              val prefix = "files"
              //println(s"claim: $jwtClaim")
              val username = jwtClaim.username.getOrElse("unknown")
              val client = jwtClaim.client_id.getOrElse("unknown")
              val filepath = s"$filename"
              val folder: File = s"$prefix"/s"$client"/s"$username"
              val file: File = s"$prefix"/s"$client"/s"$username"/s"""$filename"""
              req.content match {
                case HttpData.CompleteData(data) =>
                  //Files.write(Paths.get(filename), data.map(_.byteValue).toArray)
                  folder.createIfNotExists(asDirectory = true, createParents = true)
                  file.writeBytes(data.map(_.byteValue).toArray.toIterator)
                  Task.succeed(UploadResponse(requestId = serviceContext.requestId, message = "", path = List(s"${file.url.toString}")))
                case HttpData.StreamData(chunks)      =>
                  //println(s"chunks: $chunks")
                  Task.succeed(UploadResponse(requestId = serviceContext.requestId, message = "", path = List("")))
                case HttpData.Empty              =>
                  Task.succeed(UploadResponse(requestId = serviceContext.requestId, message = "", path = List("")))
              }
            }
          } yield Response.jsonString(results.toJson)
      }
      .catchAll {
        case NotFoundException(msg, id) =>
          Http.fail(HttpError.NotFound(Root / "columba" / "v1" / "forms" / id.toString))
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
