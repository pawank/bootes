package com.data2ui.repository

import com.data2ui.models.Models.{Element, Options}
import io.getquill.context.ZioJdbc.QDataSource
import zio._
import zio.macros.accessible


case class FieldValue(field: String, value: String)

@accessible
trait FormRepository {
  def create(element: Element): Task[Element]
  def update(element: Element): Task[Element]
  def all: Task[Seq[Element]]
  def filter(values: Seq[FieldValue]): Task[Seq[Element]]
  def findById(id: Long): Task[Element]
  def findByName(code: String): Task[Seq[Element]]
}

object ElementRepository {
  val layer: URLayer[QDataSource, Has[FormRepository]] = (FormRepositoryLive(_, _)).toLayer
}

