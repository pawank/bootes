package com.data2ui.repository

import com.bootes.dao.keycloak.Models.ServiceContext
import com.data2ui.models.Models.{CreateElementRequest, CreateFormRequest, Form, Options}
import io.getquill.context.ZioJdbc.QDataSource
import zio._
import zio.macros.accessible

import java.util.UUID
import java.time.ZonedDateTime


case class FieldValue(field: String, value: String)

@accessible
trait FormRepository {
  def upsert(form: CreateFormRequest, sectionName: String, stepNo: Int): Task[CreateFormRequest]
  def all(owner: Option[String], isTemplate: Boolean): Task[Seq[Form]]
  def submissions(formId: Option[UUID]): Task[Seq[Form]]
  def filter(values: Seq[FieldValue]): Task[Seq[Form]]
  def findById(id: UUID, isRefreshId: Boolean, seqNo: Int): Task[CreateFormRequest]
  def deleteById(id: UUID): Task[Option[String]]
  def deleteTemplateForm(id: UUID, forced: Option[Boolean]): Task[Option[String]]
  def findByTitle(code: String): Task[Seq[Form]]
  def uploadFile(id: UUID, formId: Option[UUID], filename: Option[String]): Task[CreateElementRequest]
  def uploadFile(element: CreateElementRequest): Task[CreateElementRequest]
}

object FormRepository {
  val layer: URLayer[QDataSource with Has[FormElementsRepository] with Has[OptionsRepository] with Has[ValidationsRepository], Has[FormRepository]] = (FormRepositoryLive(_, _)).toLayer
}

