package com.data2ui.repository

import com.data2ui.models.Models.{Element, Options, Validations}
import com.data2ui.repository.ElementQueries.{elementsQuery, upsert}
import com.data2ui.repository.FormElementsRepository
import com.data2ui.repository.repository.NotFoundException
import io.getquill.Ord
import io.getquill.context.ZioJdbc.QuillZioExt
import zio._
import zio.blocking.Blocking

import java.io.Closeable
import java.util.UUID
import javax.sql.DataSource

case class FormElementsRepositoryLive(dataSource: DataSource with Closeable, blocking: Blocking.Service) extends FormElementsRepository {
  val dataSourceLayer: Has[DataSource with Closeable] with Has[Blocking.Service] = Has.allOf[DataSource with Closeable, Blocking.Service](dataSource, blocking)

  import com.data2ui.repository.repository.FormContext._

  override def upsert(element: Element): Task[Element] = transaction {
    for {
      id     <- run(ElementQueries.upsert(element))
      elements <- {
        run(ElementQueries.byId(id))
      }
    } yield elements.headOption.getOrElse(throw new Exception("Upsert failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)


  override def create(element: Element): Task[Element] = transaction {
    for {
      id     <- run(ElementQueries.insertElement(element).returning(_.id))
      elements <- {
        run(ElementQueries.byId(id))
      }
    } yield elements.headOption.getOrElse(throw new Exception("Insert failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)

  override def all: Task[Seq[Element]] = run(ElementQueries.elementsQuery).dependOnDataSource().provide(dataSourceLayer)

  override def findById(id: UUID): Task[Element] = {
    for {
      results <- run(ElementQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
      element    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find element with id $id", id.toString))
    } yield element
  }

  override def findByName(name: String): Task[Seq[Element]] = {
    for {
      results <- run(ElementQueries.byName(name)).dependOnDataSource().provide(dataSourceLayer)
      xs <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find elements with input criteria, ${name.toString()}", name))
    } yield xs
  }

  override def filter(values: Seq[FieldValue]): Task[Seq[Element]] = {
    for {
      results <- run(ElementQueries.byName("")).dependOnDataSource().provide(dataSourceLayer)
      xs <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find elements with input criteria, ${values.toString()}", values.mkString(", ")))
    } yield xs
  }

  override def filterByIds(ids: List[UUID]): Task[Seq[Element]] = {
    for {
      results <- run(ElementQueries.filterByIds(ids)).dependOnDataSource().provide(dataSourceLayer)
      xs <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find elements with input criteria, ${ids.toString()}", ids.mkString(", ")))
    } yield xs
  }

  override def update(element: Element): Task[Element] = transaction {
    for {
      _     <- run(ElementQueries.upsertElement(element))
      xs <- run(ElementQueries.byId(element.id))
    } yield xs.headOption.getOrElse(throw new Exception("Update failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)

  def batchUpsert(elements: Seq[Element]): Task[Seq[Element]] = transaction {
    for {
      ids     <- run(ElementQueries.batchUpsert(elements))
      elems <- run(ElementQueries.filterByIds(ids))
      xs    <- ZIO.effect(elems).orElseFail(NotFoundException(s"Could not find elements with input criteria", ""))
    } yield xs
  }.dependOnDataSource().provide(dataSourceLayer)

}


object ElementQueries {

  import com.data2ui.repository.repository.FormContext._

  // NOTE - if you put the type here you get a 'dynamic query' - which will never wind up working...
  implicit val elementSchemaMeta = schemaMeta[Element](""""form_element"""")
  //implicit val elementInsertMeta = insertMeta[Element](_.id)

  val elementsQuery                   = quote(query[Element])
  def byId(id: UUID)               = quote(elementsQuery.filter(_.id == lift(id)))
  def byName(name: String)               = quote(elementsQuery.filter(_.name == lift(name)))
  def filter(values: Seq[FieldValue])               = quote(elementsQuery.filter(element => liftQuery(values.map(_.value)).contains(element.id)))
  def filterByIds(ids: Seq[UUID])               = quote(elementsQuery.filter(element => liftQuery(ids).contains(element.id)))
  def insertElement(element: Element) = quote(elementsQuery.insert(lift(element)))
  def upsertElement(element: Element) = quote(elementsQuery.update(lift(element)))
  def upsert(element: Element) = quote(query[Element].insert(lift(element)).onConflictUpdate(_.id)((t, e) => t.id -> e.id, (t, e) => t.seqNo -> e.seqNo, (t, e) => t.sectionName -> e.sectionName, (t, e) => t.sectionSeqNo -> e.sectionSeqNo , (t, e) => t.name -> e.name, (t, e) => t.`type` -> e.`type`, (t, e) => t.action -> e.action, (t, e) => t.optionsType -> e.optionsType, (t, e) => t.values -> e.values, (t, e) => t.formId -> e.formId, (t, e) => t.required -> e.required, (t, e) => t.errors -> e.errors, (t, e) => t.metadata.map(_.updatedAt) -> e.metadata.map(_.updatedAt), (t, e) => t.metadata.map(_.updatedBy) -> e.metadata.map(_.updatedBy)).returning(_.id))
  def batchUpsert(elements: Seq[Element]) = quote{
    liftQuery(elements).foreach(e => query[Element].insert(e).onConflictUpdate(_.id)((t, e) => t.id -> e.id, (t, e) => t.seqNo -> e.seqNo, (t, e) => t.sectionName -> e.sectionName, (t, e) => t.sectionSeqNo -> e.sectionSeqNo , (t, e) => t.name -> e.name, (t, e) => t.`type` -> e.`type`, (t, e) => t.action -> e.action, (t, e) => t.optionsType -> e.optionsType, (t, e) => t.values -> e.values, (t, e) => t.formId -> e.formId, (t, e) => t.required -> e.required, (t, e) => t.errors -> e.errors, (t, e) => t.metadata.map(_.updatedAt) -> e.metadata.map(_.updatedAt), (t, e) => t.metadata.map(_.updatedBy) -> e.metadata.map(_.updatedBy)).returning(_.id))
  }
  def byFormId(formId: UUID)               = quote(elementsQuery.filter(_.formId == lift(Option(formId))))
  def getCreateElementRequestByFormId(formId: UUID)               =   quote {
    for {
      ele <- query[Element].sortBy(p => (p.sectionSeqNo, p.seqNo))(Ord(Ord.asc, Ord.asc)).filter(x => x.formId == Option(lift(formId)))
      valid <- query[Validations].leftJoin(x => x.elementId == Option(ele.id))
      opt <- query[Options].sortBy(p => p.seqNo)(Ord.asc).leftJoin(x => x.elementId == Option(ele.id))
    } yield (ele, valid, opt)
  }
}
