package com.data2ui.repository

import zio.prelude._
import com.data2ui.models.Models.{Options, Validations}
import com.data2ui.repository.FormRepository
import com.data2ui.repository.OptionsQueries.optionsQuery
import com.data2ui.repository.repository.NotFoundException
import io.getquill.context.ZioJdbc.QuillZioExt
import zio._
import zio.blocking.Blocking

import java.io.Closeable
import java.util.UUID
import javax.sql.DataSource

case class ValidationsRepositoryLive(dataSource: DataSource with Closeable, blocking: Blocking.Service) extends ValidationsRepository {
  val dataSourceLayer: Has[DataSource with Closeable] with Has[Blocking.Service] = Has.allOf[DataSource with Closeable, Blocking.Service](dataSource, blocking)

  import com.data2ui.repository.repository.FormContext._

  override def upsert(element: Validations): Task[Validations] = transaction {
    for {
      id     <- run(ValidationsQueries.insertValidations(element).onConflictUpdate(_.id)((ext, tobeInserted) => ext -> tobeInserted).returning(_.id))
      elements <- {
        run(ValidationsQueries.byId(id))
      }
    } yield elements.headOption.getOrElse(throw new Exception("Insert failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)

  override def create(valid: Validations): Task[Validations] = transaction {
    for {
      id     <- run(ValidationsQueries.insertValidations(valid).returning(_.id))
      valids <- {
        run(ValidationsQueries.byId(id))
      }
    } yield valids.headOption.getOrElse(throw new Exception("Insert failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)

  override def all: Task[Seq[Validations]] = run(ValidationsQueries.validsQuery).dependOnDataSource().provide(dataSourceLayer)

  override def findById(id: UUID): Task[Validations] = {
    for {
      results <- run(ValidationsQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
      valid    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find valid with id $id", id.toString))
    } yield valid
  }

  override def findByType(name: String): Task[Seq[Validations]] = {
    for {
      results <- run(ValidationsQueries.byType(name)).dependOnDataSource().provide(dataSourceLayer)
      valid    <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find valids with input criteria, ${name.toString()}", name))
    } yield valid
  }

  override def filter(values: Seq[FieldValue]): Task[Seq[Validations]] = {
    for {
      results <- run(ValidationsQueries.byType("")).dependOnDataSource().provide(dataSourceLayer)
      valid    <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find valids with input criteria, ${values.toString()}", values.mkString(", ")))
    } yield valid
  }

  override def update(valid: Validations): Task[Validations] = transaction {
    for {
      _     <- run(ValidationsQueries.upsertValidations(valid))
      valids <- run(ValidationsQueries.byId(valid.id))
    } yield valids.headOption.getOrElse(throw new Exception("Update failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)

  def batchUpsert(elements: Seq[Validations]): Task[Seq[Validations]] = transaction {
    for {
      ids     <- run(ValidationsQueries.batchUpsert(elements))
      elems <- run(ValidationsQueries.filterByIds(ids))
      xs    <- ZIO.effect(elems).orElseFail(NotFoundException(s"Could not find elements with input criteria", ""))
    } yield xs
  }.dependOnDataSource().provide(dataSourceLayer)
}

object ValidationsQueries {

  import com.data2ui.repository.repository.FormContext._

  // NOTE - if you put the type here you get a 'dynamic query' - which will never wind up working...
  implicit val validsSchemaMeta = schemaMeta[Validations](""""validations"""")
  //implicit val validsInsertMeta = insertMeta[Validations](_.id)

  val validsQuery                   = quote(query[Validations])
  def byId(id: UUID)               = quote(validsQuery.filter(_.id == lift(id)))
  def filterByIds(ids: Seq[UUID])               = quote(validsQuery.filter(element => liftQuery(ids).contains(element.id)))
  def byType(value: String)               = quote(validsQuery.filter(_.`type` == lift(value)))
  def filter(values: Seq[FieldValue])               = quote(query[Validations])
  def filterByElementId(elementId: Option[UUID])               = quote(query[Validations].filter(v => v.elementId == lift(elementId)))
  def filterByElementIds(ids: Seq[UUID])               = quote(query[Validations].filter(element => liftQuery(ids).contains(element.elementId)))
  def insertValidations(valid: Validations) = quote(validsQuery.insert(lift(valid)))
  def upsertValidations(valid: Validations) = quote(validsQuery.update(lift(valid)))
  def batchUpsert(elements: Seq[Validations]) = quote{
    liftQuery(elements).foreach(e => query[Validations].insert(e).onConflictUpdate(_.id)((ext, tobeInserted) => ext.id -> tobeInserted.id, (ext, tobeInserted) => ext.elementId -> tobeInserted.elementId, (ext, tobeInserted) => ext.values -> tobeInserted.values, (ext, tobeInserted) => ext.`type` -> tobeInserted.`type`, (ext, tobeInserted) => ext.title -> tobeInserted.title, (ext, tobeInserted) => ext.minimum -> tobeInserted.minimum, (ext, tobeInserted) => ext.maximum -> tobeInserted.maximum).returning(_.id))
    //liftQuery(elements).foreach(e => query[Validations].insert(e).onConflictUpdate(_.id)((ext, tobeInserted) => ((ext.`type` -> tobeInserted.`type`, ext.values -> tobeInserted.values, ext.title -> tobeInserted.title, ext.minimum -> tobeInserted.minimum, ext.maximum -> tobeInserted.maximum, ext.elementId -> tobeInserted.elementId))).returning(_.id))
  }
}