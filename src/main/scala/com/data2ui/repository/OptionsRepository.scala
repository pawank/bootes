package com.data2ui.repository

import com.data2ui.models.Models.{Element, Options}
import io.getquill.context.ZioJdbc.QDataSource
import zio._
import zio.macros.accessible

@accessible
trait OptionsRepository {
  def create(option: Options): Task[Options]
  def update(option: Options): Task[Options]
  def all: Task[Seq[Options]]
  def filter(values: Seq[FieldValue]): Task[Seq[Options]]
  def findById(id: Long): Task[Options]
  def findByName(code: String): Task[Seq[Options]]
}

object OptionsRepository {
  val layer: URLayer[QDataSource, Has[OptionsRepository]] = (OptionsRepositoryLive(_, _)).toLayer
}
