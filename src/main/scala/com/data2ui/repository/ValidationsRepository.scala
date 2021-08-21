package com.data2ui.repository

import com.data2ui.models.Models.{Element, Validations}
import io.getquill.context.ZioJdbc.QDataSource
import zio._
import zio.macros.accessible

@accessible
trait ValidationsRepository {
  def create(option: Validations): Task[Validations]
  def update(option: Validations): Task[Validations]
  def all: Task[Seq[Validations]]
  def filter(values: Seq[FieldValue]): Task[Seq[Validations]]
  def findById(id: Long): Task[Validations]
  def findByType(`type`: String): Task[Seq[Validations]]
}

object ValidationsRepository {
  val layer: URLayer[QDataSource, Has[ValidationsRepository]] = (ValidationsRepositoryLive(_, _)).toLayer
}
