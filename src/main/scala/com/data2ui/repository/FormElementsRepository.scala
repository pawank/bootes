package com.data2ui.repository

import com.data2ui.models.Models.{Element, Options}
import io.getquill.context.ZioJdbc.QDataSource
import zio._
import zio.macros.accessible

import java.util.UUID


@accessible
trait FormElementsRepository {
  def upsert(element: Element): Task[Element]
  def batchUpsert(elements: Seq[Element]): Task[Seq[Element]]
  def create(element: Element): Task[Element]
  def update(element: Element): Task[Element]
  def all: Task[Seq[Element]]
  def filter(values: Seq[FieldValue]): Task[Seq[Element]]
  def filterByIds(ids: List[UUID]): Task[Seq[Element]]
  def findById(id: UUID): Task[Element]
  def findByName(code: String): Task[Seq[Element]]
}

object FormElementsRepository {
  val layer: URLayer[QDataSource with Has[OptionsRepository] with Has[ValidationsRepository], Has[FormElementsRepository]] = (FormElementsRepositoryLive(_, _)).toLayer
}

