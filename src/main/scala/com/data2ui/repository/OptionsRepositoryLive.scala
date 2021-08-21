package com.data2ui.repository

import com.data2ui.models.Models.{Options}
import com.data2ui.repository.FormRepository
import com.data2ui.repository.repository.NotFoundException
import io.getquill.context.ZioJdbc.QuillZioExt
import zio._
import zio.blocking.Blocking

import java.io.Closeable
import javax.sql.DataSource

case class OptionsRepositoryLive(dataSource: DataSource with Closeable, blocking: Blocking.Service) extends OptionsRepository {
  val dataSourceLayer: Has[DataSource with Closeable] with Has[Blocking.Service] = Has.allOf[DataSource with Closeable, Blocking.Service](dataSource, blocking)

  import com.data2ui.repository.repository.FormContext._

  override def create(option: Options): Task[Options] = transaction {
    for {
      id     <- run(OptionsQueries.insertOptions(option).returning(_.id))
      options <- {
        run(OptionsQueries.byId(id))
      }
    } yield options.headOption.getOrElse(throw new Exception("Insert failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)

  override def all: Task[Seq[Options]] = run(OptionsQueries.optionsQuery).dependOnDataSource().provide(dataSourceLayer)

  override def findById(id: Long): Task[Options] = {
    for {
      results <- run(OptionsQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
      option    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find option with id $id", id.toString))
    } yield option
  }

  override def findByName(name: String): Task[Seq[Options]] = {
    for {
      results <- run(OptionsQueries.byName(name)).dependOnDataSource().provide(dataSourceLayer)
      option    <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find options with input criteria, ${name.toString()}", name))
    } yield option
  }

  override def filter(values: Seq[FieldValue]): Task[Seq[Options]] = {
    for {
      results <- run(OptionsQueries.byName("")).dependOnDataSource().provide(dataSourceLayer)
      option    <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find options with input criteria, ${values.toString()}", values.mkString(", ")))
    } yield option
  }

  override def update(option: Options): Task[Options] = transaction {
    for {
      _     <- run(OptionsQueries.upsertOptions(option))
      options <- run(OptionsQueries.byId(option.id))
    } yield options.headOption.getOrElse(throw new Exception("Update failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)
}

object OptionsQueries {

  import com.data2ui.repository.repository.FormContext._

  // NOTE - if you put the type here you get a 'dynamic query' - which will never wind up working...
  implicit val optionsSchemaMeta = schemaMeta[Options](""""options"""")
  implicit val optionsInsertMeta = insertMeta[Options](_.id)

  val optionsQuery                   = quote(query[Options])
  def byId(id: Long)               = quote(optionsQuery.filter(_.id == lift(id)))
  def byName(value: String)               = quote(optionsQuery.filter(_.value == lift(value)))
  def filter(values: Seq[FieldValue])               = quote(query[Options])
  def insertOptions(option: Options) = quote(optionsQuery.insert(lift(option)))
  def upsertOptions(option: Options) = quote(optionsQuery.update(lift(option)))
}