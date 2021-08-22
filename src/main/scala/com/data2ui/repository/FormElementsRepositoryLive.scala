package com.data2ui.repository

import com.data2ui.models.Models.{Element, Options}
import com.data2ui.repository.FormElementsRepository
import com.data2ui.repository.repository.NotFoundException
import io.getquill.context.ZioJdbc.QuillZioExt
import zio._
import zio.blocking.Blocking

import java.io.Closeable
import javax.sql.DataSource

case class FormElementsRepositoryLive(dataSource: DataSource with Closeable, blocking: Blocking.Service) extends FormElementsRepository {
  val dataSourceLayer: Has[DataSource with Closeable] with Has[Blocking.Service] = Has.allOf[DataSource with Closeable, Blocking.Service](dataSource, blocking)

  import com.data2ui.repository.repository.FormContext._

  override def upsert(element: Element): Task[Element] = transaction {
    for {
      id     <- run(ElementQueries.insertElement(element).onConflictUpdate(_.id)((ext, tobeInserted) => ext -> tobeInserted).returning(_.id))
      elements <- {
        run(ElementQueries.byId(id))
      }
    } yield elements.headOption.getOrElse(throw new Exception("Insert failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)

  /*
  override def batchUpsert(elements: Seq[Element]): Task[Seq[Element]] = transaction {
    for {
      ids     <- run(liftQuery(elements).foreach(element => ElementQueries.insertElement(element).onConflictUpdate(_.id)((ext, tobeInserted) => ext -> tobeInserted).returning(_.id)))
      elements <- run(ElementQueries.filterByIds(ids.map(id => id)))
      xs    <- ZIO.effect(elements).orElseFail(NotFoundException(s"Could not find elements with input criteria", ""))
    } yield xs
  }.dependOnDataSource().provide(dataSourceLayer)
  */
  override def create(element: Element): Task[Element] = transaction {
    for {
      id     <- run(ElementQueries.insertElement(element).returning(_.id))
      elements <- {
        run(ElementQueries.byId(id))
      }
    } yield elements.headOption.getOrElse(throw new Exception("Insert failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)

  override def all: Task[Seq[Element]] = run(ElementQueries.elementsQuery).dependOnDataSource().provide(dataSourceLayer)

  override def findById(id: Long): Task[Element] = {
    for {
      results <- run(ElementQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
      element    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find element with id $id", id.toString))
    } yield element
  }

  override def findByName(name: String): Task[Seq[Element]] = {
    for {
      results <- run(ElementQueries.byName(name)).dependOnDataSource().provide(dataSourceLayer)
      element    <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find elements with input criteria, ${name.toString()}", name))
    } yield element
  }

  override def filter(values: Seq[FieldValue]): Task[Seq[Element]] = {
    for {
      results <- run(ElementQueries.byName("")).dependOnDataSource().provide(dataSourceLayer)
      element    <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find elements with input criteria, ${values.toString()}", values.mkString(", ")))
    } yield element
  }

  override def filterByIds(ids: List[Long]): Task[Seq[Element]] = {
    for {
      results <- run(ElementQueries.filterByIds(ids)).dependOnDataSource().provide(dataSourceLayer)
      element    <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find elements with input criteria, ${ids.toString()}", ids.mkString(", ")))
    } yield element
  }

  override def update(element: Element): Task[Element] = transaction {
    for {
      _     <- run(ElementQueries.upsertElement(element))
      elements <- run(ElementQueries.byId(element.id))
    } yield elements.headOption.getOrElse(throw new Exception("Update failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)
}


object ElementQueries {

  import com.data2ui.repository.repository.FormContext._

  // NOTE - if you put the type here you get a 'dynamic query' - which will never wind up working...
  implicit val elementSchemaMeta = schemaMeta[Element](""""form_element"""")
  implicit val elementInsertMeta = insertMeta[Element](_.id)

  val elementsQuery                   = quote(query[Element])
  def byId(id: Long)               = quote(elementsQuery.filter(_.id == lift(id)))
  def byName(name: String)               = quote(elementsQuery.filter(_.name == lift(name)))
  def filter(values: Seq[FieldValue])               = quote(elementsQuery.filter(element => liftQuery(values.map(_.value)).contains(element.id)))
  def filterByIds(ids: Seq[Long])               = quote(elementsQuery.filter(element => liftQuery(ids).contains(element.id)))
  def insertElement(element: Element) = quote(elementsQuery.insert(lift(element)))
  def upsertElement(element: Element) = quote(elementsQuery.update(lift(element)))
}
