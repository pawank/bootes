package com.data2ui

import com.bootes.dao.{Metadata, ResponseMessage}
import com.bootes.dao.keycloak.Models.{ApiResponseError, ApiResponseSuccess, Attributes, Email, KeycloakUser, Phone, ServiceContext}
import com.bootes.dao.repository.{JSONB, UserRepository}
import com.bootes.server.auth.{ApiToken, LogoutRequest}
import com.data2ui.models.Models.{CreateElementRequest, CreateFormRequest, Form}
import com.data2ui.repository.FormRepository
import io.getquill.Embedded
import io.scalaland.chimney.dsl.TransformerOps
import sttp.client3.{Response, basicRequest}
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.clock.Clock
import zio.{Has, IO, RIO, RLayer, Task, ZIO, system}
import zio.console.Console
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.macros.accessible
import zio.console._
import zio.json._
import zio.logging.{LogAnnotation, Logging, log}
import zio.prelude.Validation

import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.language.implicitConversions


@accessible
trait FormService {
  def create(request: CreateFormRequest)(implicit ctx: ServiceContext): Task[Form]
  def update(id: UUID, request: CreateFormRequest)(implicit ctx: ServiceContext): Task[Form]
  def upsert(request: CreateFormRequest)(implicit ctx: ServiceContext): Task[CreateFormRequest]
  def submit(request: CreateFormRequest, sectionName: String, stepNo: Int)(implicit ctx: ServiceContext): Task[CreateFormRequest]
  def all(owner: Option[String], isTemplate: Boolean)(implicit ctx: ServiceContext): Task[Seq[Form]]
  def get(id: UUID, seqNo: Int)(implicit ctx: ServiceContext): Task[CreateFormRequest]
  def delete(id: UUID)(implicit ctx: ServiceContext): Task[Option[String]]
  def deleteTemplateForm(id: UUID, forced: Option[Boolean])(implicit ctx: ServiceContext): Task[Option[String]]
  def getTemplateForm(id: UUID)(implicit ctx: ServiceContext): Task[CreateFormRequest]
  def uploadFile(id: UUID, formId: Option[UUID], filename: Option[String])(implicit ctx: ServiceContext): Task[CreateElementRequest]
  def uploadFile(element: CreateElementRequest)(implicit ctx: ServiceContext): Task[CreateElementRequest]
  def getByEmail(email: String)(implicit ctx: ServiceContext): Task[Form]
  def logout(id: String, inputRequest: LogoutRequest)(implicit ctx: ServiceContext): Task[ResponseMessage]
}

object FormService {
  val layer: RLayer[Has[FormRepository] with Console, Has[FormService]] = (FormServiceLive(_, _)).toLayer
}

case class FormServiceLive(repository: FormRepository, console: Console.Service) extends FormService {

  override def create(request: CreateFormRequest)(implicit ctx: ServiceContext): Task[Form] = {
    ???
  }

  override def upsert(request: CreateFormRequest)(implicit ctx: ServiceContext): Task[CreateFormRequest] = {
    repository.upsert(request, "", stepNo = -1)
  }

  override def submit(request: CreateFormRequest, sectionName: String, stepNo: Int)(implicit ctx: ServiceContext): Task[CreateFormRequest] = {
    println(s"submit: request = $request, sectionName = $sectionName and stepNo = $stepNo")
    repository.upsert(request, sectionName, stepNo)
  }

  override def all(owner: Option[String], isTemplate:Boolean)(implicit ctx: ServiceContext): Task[Seq[Form]] = for {
    elements <- repository.all(owner, isTemplate)
    _     <- console.putStrLn(s"Forms: ${elements.map(_.title).mkString(",")}")
  } yield elements.sortBy(_.id)

  override def get(id: UUID, seqNo: Int)(implicit ctx: ServiceContext): Task[CreateFormRequest] = repository.findById(id, isRefreshId = true, seqNo)
  override def delete(id: UUID)(implicit ctx: ServiceContext): Task[Option[String]] = repository.deleteById(id)
  override def deleteTemplateForm(id: UUID, forced: Option[Boolean])(implicit ctx: ServiceContext): Task[Option[String]] = repository.deleteTemplateForm(id, forced)
  override def getTemplateForm(id: UUID)(implicit ctx: ServiceContext): Task[CreateFormRequest] = repository.findById(id, isRefreshId = false, seqNo = 0)

  override def update(id: UUID, request: CreateFormRequest)(implicit ctx: ServiceContext): Task[Form] =  {
    ???
  }

  override def getByEmail(email: String)(implicit ctx: ServiceContext): Task[Form] = ???
  
  override def uploadFile(id: UUID, formId: Option[UUID], filename: Option[String])(implicit ctx: ServiceContext): Task[CreateElementRequest] = repository.uploadFile(id, formId, filename)
  override def uploadFile(element: CreateElementRequest)(implicit ctx: ServiceContext): Task[CreateElementRequest] = repository.uploadFile(element)

  def logout(id: String, inputRequest: LogoutRequest)(implicit ctx: ServiceContext): Task[ResponseMessage] = Task.succeed(ResponseMessage.makeSuccess(200, ""))
}
