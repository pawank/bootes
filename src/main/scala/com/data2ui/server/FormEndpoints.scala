package com.data2ui.server

import com.bootes.dao.keycloak.Models.ServiceContext
import com.bootes.dao.repository.NotFoundException
import com.bootes.server.UserEndpoints.getServiceContext
import com.bootes.server.UserServer
import com.bootes.server.UserServer.{CorrelationId, DebugJsonLog}
import com.bootes.server.auth.{ApiToken, LogoutRequest, Token}
import com.data2ui.FormService
import com.data2ui.models.Models.{CreateFormRequest, UiResponse, UploadResponse}
import io.netty.handler.codec.smtp.SmtpRequests.data
import pdi.jwt.JwtClaim
import scribe.Logger.system
import zhttp.http._
import zio.console._
import zio.duration.durationInt
import zio.json._
import zio.{Chunk, Has, IO, Task, UIO, ZIO}
import zio.logging._
import zio.logging.slf4j._
import zio.stream.ZStream

import java.io.{FileInputStream, IOException}
import java.nio.file.{Files, Paths}
import java.util.UUID

object FormEndpoints extends RequestOps {
  val responseHeaders = List(Header.contentTypeJson, Header("Access-Control-Allow-Origin", "*"), Header("Access-Control-Allow-Headers", "Origin, Content-Type, Accept, X-Auth-Token"), Header("Access-Control-Allow-Credentials", "true"), Header("Access-Control-Expose-Headers", "Content-Length"), Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS"))

  def generateJsonResponseWithCorsHeaders(data: String, status: Option[Status] = None) =  {
            Response.http(
              status = status.getOrElse(Status.OK),
              content = HttpData.CompleteData(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
              headers = responseHeaders
            )
  }

  val form: ApiToken => Http[Has[FormService] with Console with Logging, HttpError, Request, UResponse] = jwtClaim => {
    implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)


    Http
      .collectM[Request] {
        case req @ Method.GET -> Root / "columba" / "v1" / "forms" / "submissions" =>
          val formId: Option[String] = (req.url.queryParams.get("formId") match {
            case Some(value) => value.headOption
            case _ => None
          })
          for {
            //_ <- ZIO.succeed(scribe.info("Getting list of all forms"))
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.debug(s"Calling form service for fetching all submitted forms matching with id, $formId")
            )
            allForms <- FormService.getAll(if (formId.isDefined && !formId.get.isEmpty()) Seq(formId.map(UUID.fromString(_)).getOrElse(UUID.randomUUID())) else Seq.empty)
            _ <- log.debug(s"Showing all submissions for formId = $formId")
            //forms <- FormService.submissions(formId.map(UUID.fromString(_)))
          } yield {
            generateJsonResponseWithCorsHeaders(allForms.toJson)
          }
        case req @ Method.GET -> Root / "columba" / "v1" / "forms" / "search" =>
          val createdBy = req.url.queryParams.get("createdBy") match {
            case Some(xs) =>
              xs.headOption
            case _ =>
              jwtClaim.username
          }
          val isTemplate = req.url.queryParams.get("template") match {
            case Some(xs) =>
              xs.contains("true") || xs.contains("True")
            case _ =>
              false
          }
          for {
            //_ <- ZIO.succeed(scribe.info("Getting list of all forms"))
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.debug(s"Calling form service for fetching all forms matching with createdBy or owned by, $createdBy")
            )
            forms <- FormService.all(createdBy, isTemplate)
          } yield {
            generateJsonResponseWithCorsHeaders(forms.toJson)
          }
        case req @ Method.GET -> Root / "columba" / "v1" / "forms" / id =>
          val sectionSeqNo = (req.url.queryParams.get("seqNo") match {
            case Some(xs) =>
              xs.headOption.getOrElse("0")
            case _ =>
              "0"
          }).toInt
          for {
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.info(s"Fetching form, $id with seqNo, $sectionSeqNo")
            )
            userForm <- {
              //println(s"Form ID = $id with seqNo = $sectionSeqNo")
              FormService.get(UUID.fromString(id), seqNo = sectionSeqNo)
            }
          } yield {
            generateJsonResponseWithCorsHeaders(userForm.toJson)
          }
        case req @ Method.GET -> Root / "columba" / "v1" / "forms" / "template" / id =>
          for {
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.info(s"Fetching form template by id, $id")
            )
            form <- {
              //println(s"Form ID = $id")
              FormService.getTemplateForm(UUID.fromString(id))
            }
          } yield {
            generateJsonResponseWithCorsHeaders(form.toJson)
          }
        case req@Method.POST -> Root / "columba" / "v1" / "forms" =>
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
              //println(s"Ordered req = $orderedReq\n\n")
              val updatedMetadata = orderedReq.metadata.map(m => m.copy(createdBy = jwtClaim.username.getOrElse(""), updatedBy = jwtClaim.username))
              if (validation) {
                val validatedForm = CreateFormRequest.validate(orderedReq.copy(metadata = updatedMetadata))
                //println(s"validatedForm = $validatedForm")
                if (validatedForm.hasErrors) Task.succeed(validatedForm) else FormService.upsert(validatedForm)(serviceContext.copy(requestId = request.requestId.getOrElse(serviceContext.requestId)))
              } else FormService.upsert(orderedReq.copy(metadata = updatedMetadata))(serviceContext.copy(requestId = request.requestId.getOrElse(serviceContext.requestId)))
            }
          } yield {
            generateJsonResponseWithCorsHeaders(results.toJson)
          }
        case req@Method.POST -> Root / "columba" / "v1" / "forms" / sectionName / stepNo =>
          for {
            request <- extractBodyFromJson[CreateFormRequest](req)
            results <- {
              val orderedReq = request.copy(sections = request.sections.map(s => s.copy(elements = s.makeElementsOrdered())))
              //println(s"Route sectionName = $orderedReq")
              val validatedForm = CreateFormRequest.validate(orderedReq)
              if (validatedForm.hasErrors) Task.succeed(validatedForm) else FormService.submit(orderedReq, sectionName, stepNo.toInt)(serviceContext.copy(requestId = request.requestId.getOrElse(serviceContext.requestId)))
            }
          } yield {
            generateJsonResponseWithCorsHeaders(results.toJson)
          }
        case req@Method.POST -> Root / "columba" / "v1" / "upload" =>
          val elementId = (req.url.queryParams.get("id") match {
            case Some(xs) =>
              xs.headOption
            case _ =>
              None
          })
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
          val username = jwtClaim.username.getOrElse("unknown")
          val client = jwtClaim.client_id.getOrElse("unknown")
          for {
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.info(s"Uploading file for form, $formId for username, $username")
            )
              results <- {
                println(s"elementId = $elementId, formId = $formId and filename = $filename fo upload")
              elementId match {
                case Some(id) =>
                  import better.files._
                  import File._
                  import java.io.{File => JFile}
                  val uid = UUID.fromString(id)
                  val prefix = "files"
                  //println(s"claim: $jwtClaim")
                  val filepath = s"$filename"
                  val folder: File = s"$prefix"/s"$client"/s"$username"/s"$uid"
                  val file: File = s"$prefix"/s"$client"/s"$username"/s"$uid"/s"""$filename"""
                  req.content match {
                    case HttpData.CompleteData(data) =>
                      //Files.write(Paths.get(filename), data.map(_.byteValue).toArray)
                      folder.createIfNotExists(asDirectory = true, createParents = true)
                      file.writeBytes(data.map(_.byteValue).toArray.toIterator)
                      val path = s"/columba/v1/files/${uid.toString}"
                      Task.succeed(UploadResponse(id = Some(uid), message = "", filename = filename, path = Some(s"${path}")))
                    case HttpData.StreamData(chunks)      =>
                      //println(s"chunks: $chunks")
                      Task.succeed(UploadResponse(id = Some(uid), message = "", filename = filename,  path = None))
                    case HttpData.Empty              =>
                      Task.succeed(UploadResponse(id = Some(uid), message = "", filename = filename, path = None))
                  }
                case _ =>
                  val uid = UUID.randomUUID()
                  println(s"Unwanted elementId = $uid found during upload")
                  Task.succeed(UploadResponse(id = None, message = "No element identifier provided.", filename = filename, path = None))
              }
            }
            elementMaybe <- FormService.uploadFile(results.id.getOrElse(UUID.randomUUID()), None, results.path)
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.info(s"Uploaded file, ${results.filename} for form, $formId for username, $username")
            )
          } yield {
            println(s"Element saved $elementMaybe")
            generateJsonResponseWithCorsHeaders(results.toJson)
          }

        case Method.DELETE -> Root / "columba" / "v1" / "forms" / "clearall" / id =>
          for {
            maybeError <- {
              //println(s"Form ID = $id")
              FormService.delete(UUID.fromString(id))
            }
            r <- Task.succeed(UiResponse(requestId = serviceContext.requestId.toString, status = if (maybeError.isDefined) false else true, message = maybeError.getOrElse(""), code = "204", data = List.empty))
          } yield {
            generateJsonResponseWithCorsHeaders(r.toJson)
          }

        case req @ Method.DELETE -> Root / "columba" / "v1" / "forms" / "template" / id =>

          val forced = (req.url.queryParams.get("forced") match {
            case Some(xs) =>
              xs.headOption.map(_.toBoolean)
            case _ =>
              None
          })
          for {
            maybeError <- {
              //println(s"Form ID = $id")
              FormService.deleteTemplateForm(UUID.fromString(id), forced)
            }
            r <- Task.succeed(UiResponse(requestId = serviceContext.requestId.toString, status = if (maybeError.isDefined) false else true, message = maybeError.getOrElse(""), code = "204", data = List.empty))
          } yield {
            generateJsonResponseWithCorsHeaders(r.toJson)
          }
      }
      .catchAll {
        case NotFoundException(msg, id) =>
          println(s"NotFoundException message = $msg for id = $id")
          //Http.fail(HttpError.NotFound(Root / "columba" / "v1" / "forms" / id.toString))
          Http.succeed(generateJsonResponseWithCorsHeaders({Root / "columba" / "v1" / "forms" / id.toString}.toString, status = Some(Status.NOT_FOUND)))
        case ex: Throwable =>
          val error = ex.getMessage
          val finalError = if (error.contains("(missing)")) {
            val tokens = error.replaceAll("""\(missing\)""","").split("""\.""")
            if (tokens.size >= 2) s"""${tokens(1)} is missing""" else tokens(0)
          } else error
          println(s"Forms Exception: $finalError")
          //Http.fail(HttpError.InternalServerError(msg = finalError, cause = None))
          Http.succeed(generateJsonResponseWithCorsHeaders(finalError, status = Some(Status.INTERNAL_SERVER_ERROR)))
        case err =>
          val error = err.toString
          println(s"Forms Uncatched Exception: $error")
          if (error.contains("(missing)")) {
            val tokens = error.replaceAll("""\(missing\)""","")
            val finalError = s"""${tokens.substring(1)} is missing"""
            println(s"Forms ERROR: $finalError")
            //Http.fail(HttpError.BadRequest(msg = finalError))
            Http.succeed(generateJsonResponseWithCorsHeaders(finalError, status = Some(Status.BAD_REQUEST)))
          } else {
            //Http.fail(HttpError.InternalServerError(msg = error))
            Http.succeed(generateJsonResponseWithCorsHeaders(error, status = Some(Status.INTERNAL_SERVER_ERROR)))
          }
      }
  }

  val formOtherRoutes: ApiToken => Http[Logging, Nothing, Request, Response[zio.blocking.Blocking, Throwable]] = jwtClaim => {
    Http
      .collectM[Request] {
        case req@Method.GET -> Root / "columba" / "v1" / "files" / id =>
          implicit val serviceContext: ServiceContext = getServiceContext(jwtClaim)
          val filename = (req.url.queryParams.get("filename") match {
            case Some(xs) =>
              xs.headOption
            case _ =>
              None
          }).getOrElse(s"${UUID.randomUUID().toString}.dat")
          val username = jwtClaim.username.getOrElse("unknown")
          val client = jwtClaim.client_id.getOrElse("unknown")
          for {
            _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(serviceContext.toString)))(
              log.info(s"Fetching file for filename, $filename for username, $username")
            )
          } yield {
            import better.files._
            import File._
            import java.io.{File => JFile}
            val uid = id
            val prefix = "files"
            //println(s"claim: $jwtClaim")
            val filepath = s"$filename"
            val folder: File = s"$prefix"/s"$client"/s"$username"/s"$uid"
            val file: File = s"$prefix"/s"$client"/s"$username"/s"$uid"/s"""$filename"""
            val content = HttpData.fromStream {
              ZStream.fromFile(Paths.get(file.path.toString))//.orElse(ZStream.empty)
            }
            Response.http(content = content)
          }
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
