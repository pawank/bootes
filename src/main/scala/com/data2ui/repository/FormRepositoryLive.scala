package com.data2ui.repository

import com.data2ui.models.Models.{CreateElementRequest, CreateFormRequest, Element, Form, FormSection, Options, Validations}
import com.data2ui.repository.ElementQueries.elementsQuery
import com.data2ui.repository.FormRepository
import com.data2ui.repository.repository.NotFoundException
import io.getquill.context.ZioJdbc.QuillZioExt
import zio._
import zio.blocking.Blocking

import java.io.Closeable
import javax.sql.DataSource

case class FormRepositoryLive(dataSource: DataSource with Closeable, blocking: Blocking.Service) extends FormRepository {
  val dataSourceLayer: Has[DataSource with Closeable] with Has[Blocking.Service] = Has.allOf[DataSource with Closeable, Blocking.Service](dataSource, blocking)

  import com.data2ui.repository.repository.FormContext._

  override def upsert(form: CreateFormRequest): Task[Form] = {
    val dbForm = CreateFormRequest.toForm(form)
    println(s"Form to be inserted or updated = $dbForm")
    transaction {
      for {
        id     <- run(FormQueries.upsert(dbForm))
        requestedElements = form.getFormElements()
        elements: Seq[Element] = requestedElements.map(CreateElementRequest.toElement(_)).map(e => e.copy(formId = Some(id)))
        savedElements <- {
          run(ElementQueries.batchUpsert(elements))
        }
        savedValids <- {
          val xs: Map[Long, Seq[Validations]] = requestedElements.groupBy(_.id).map(v => (v._1, v._2.map(_.validations).flatten.map(x => x.copy(elementId = Some(v._1)))))
          run(ValidationsQueries.batchUpsert(xs.values.toSeq.flatten))
        }
        savedOpts <- {
          val options: Map[Long, Seq[Options]] = requestedElements.groupBy(_.id).map(v => (v._1, v._2.map(_.options.getOrElse(Seq.empty)).flatten.map(x => x.copy(elementId = Some(v._1)))))
          run(OptionsQueries.batchUpsert(options.values.toSeq.flatten))
        }
        fetchedElements <- {
          run(ElementQueries.getCreateElementRequestByFormId(id))
        }
        xs <- {
          run(FormQueries.byId(id))
        }
      } yield xs.headOption.getOrElse(throw new Exception("Insert or update failed!"))
    }.dependOnDataSource().provide(dataSourceLayer)
  }

  override def all: Task[Seq[Form]] = run(FormQueries.elementsQuery).dependOnDataSource().provide(dataSourceLayer)

  override def findById(id: Long): Task[Form] = {
    for {
      results <- run(FormQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
      element    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find element with id $id", id.toString))
    } yield element
  }

  override def findByTitle(name: String): Task[Seq[Form]] = {
    for {
      results <- run(FormQueries.byTitle(name)).dependOnDataSource().provide(dataSourceLayer)
      xs <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find elements with input criteria, ${name.toString()}", name))
    } yield xs
  }

  override def filter(values: Seq[FieldValue]): Task[Seq[Form]] = {
    for {
      results <- run(FormQueries.byTitle("")).dependOnDataSource().provide(dataSourceLayer)
      xs <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find elements with input criteria, ${values.toString()}", values.mkString(", ")))
    } yield xs
  }
}


object FormQueries {

  import com.data2ui.repository.repository.FormContext._

  // NOTE - if you put the type here you get a 'dynamic query' - which will never wind up working...
  implicit val elementSchemaMeta = schemaMeta[Form](""""form"""")
  //implicit val elementInsertMeta = insertMeta[Form](_.id)

  val elementsQuery                   = quote(query[Form])
  def byId(id: Long)               = quote(elementsQuery.filter(_.id == lift(id)))
  def byTitle(name: String)               = quote(elementsQuery.filter(_.title == lift(name)))
  def filter(values: Seq[FieldValue])               = quote(query[Form])
  def insertForm(element: Form) = quote(elementsQuery.insert(lift(element)))
  def upsertForm(element: Form) = quote(elementsQuery.update(lift(element)))
  def upsert(element: Form) = quote(elementsQuery.insert(lift(element)).onConflictUpdate(_.id)((t, e) => t.id -> e.id, (t, e) => t.uid -> e.uid, (t, e) => t.title -> e.title, (t, e) => t.subTitle -> e.subTitle, (t, e) => t.status -> e.status, (t, e) => t.designProperties.map(_.width) -> e.designProperties.map(_.width), (t, e) => t.designProperties.map(_.height) -> e.designProperties.map(_.height),(t, e) => t.designProperties.map(_.fontFamily) -> e.designProperties.map(_.fontFamily),(t, e) => t.designProperties.map(_.backgroundColor) -> e.designProperties.map(_.backgroundColor), (t, e) => t.designProperties.map(_.textColor) -> e.designProperties.map(_.textColor), (t, e) => t.metadata.map(_.updatedAt) -> e.metadata.map(_.updatedAt), (t, e) => t.metadata.map(_.updatedBy) -> e.metadata.map(_.updatedBy)).returning(_.id))
}
