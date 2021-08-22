package com.data2ui.repository

import com.data2ui.models.Models.{CreateFormRequest, Form, Options}
import io.getquill.context.ZioJdbc.QDataSource
import zio._
import zio.macros.accessible


case class FieldValue(field: String, value: String)

@accessible
trait FormRepository {
  def upsert(form: CreateFormRequest): Task[Form]
  def all: Task[Seq[Form]]
  def filter(values: Seq[FieldValue]): Task[Seq[Form]]
  def findById(id: Long): Task[Form]
  def findByTitle(code: String): Task[Seq[Form]]
}

object FormRepository {
  val layer: URLayer[QDataSource with Has[FormElementsRepository], Has[FormRepository]] = (FormRepositoryLive(_, _)).toLayer
}

