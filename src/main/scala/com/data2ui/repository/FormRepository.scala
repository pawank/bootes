package com.data2ui.repository

import com.data2ui.models.Models.{CreateFormRequest, Form, Options}
import io.getquill.context.ZioJdbc.QDataSource
import zio._
import zio.macros.accessible

import java.util.UUID


case class FieldValue(field: String, value: String)

@accessible
trait FormRepository {
  def upsert(form: CreateFormRequest, sectionName: String, stepNo: Int): Task[CreateFormRequest]
  def all: Task[Seq[Form]]
  def filter(values: Seq[FieldValue]): Task[Seq[Form]]
  def findById(id: UUID, isRefreshId: Boolean): Task[CreateFormRequest]
  def findByTitle(code: String): Task[Seq[Form]]
}

object FormRepository {
  val layer: URLayer[QDataSource with Has[FormElementsRepository], Has[FormRepository]] = (FormRepositoryLive(_, _)).toLayer
}

