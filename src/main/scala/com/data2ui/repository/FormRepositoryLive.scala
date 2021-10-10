package com.data2ui.repository

import com.bootes.dao.Metadata
import com.bootes.dao.ZioQuillExample.ctx
import com.data2ui.models.Models.{CreateElementRequest, CreateFormRequest, Element, Form, FormSection, Options, Validations}
import com.data2ui.repository.ElementQueries.elementsQuery
import com.data2ui.repository.FormRepository
import com.data2ui.repository.repository.FormContext.run
import com.data2ui.repository.repository.NotFoundException
import io.getquill.Query
import io.getquill.context.ZioJdbc.QuillZioExt
import zio._
import zio.blocking.Blocking
import zio.json.EncoderOps

import java.io.Closeable
import java.util.UUID
import javax.sql.DataSource
import scala.::
import scala.collection.immutable.SortedMap

case class FormRepositoryLive(dataSource: DataSource with Closeable, blocking: Blocking.Service) extends FormRepository {
  val dataSourceLayer: Has[DataSource with Closeable] with Has[Blocking.Service] = Has.allOf[DataSource with Closeable, Blocking.Service](dataSource, blocking)

  import com.data2ui.repository.repository.FormContext._

  def applyIdChangesOnForm(form: CreateFormRequest, sectionName: String, stepNo:Int): CreateFormRequest = {
    val formId = UUID.randomUUID()
    val sections = form.sections.map(s => {
      s.copy(elements = s.elements.map(e => {
        val eid = UUID.randomUUID()
        e.copy(id = eid, validations = e.validations.map(_.copy(id = UUID.randomUUID(), elementId = Some(eid))), options = e.options.map(opts => opts.map(_.copy(id = UUID.randomUUID(), elementId = Some(eid)))))
      })
      )
    })
    form.copy(id = formId, sections = sections)
  }

  def applySectionAndStepFilteringOnForm(form: CreateFormRequest, sectionName: String, stepNo:Int): CreateFormRequest = {
    //println(s"applySectionAndStepFilteringOnForm: sectionName = $sectionName and stepNo = $stepNo")
    //val sections = form.sections.filter(s => if (sectionName.isEmpty) true else s.title.equalsIgnoreCase(sectionName)).map(s => {
    val sections = form.sections.map(s => {
        s.copy(elements = s.elements.filter(e => e.seqNo.getOrElse(0) == stepNo))
      //s.copy(elements = s.elements.drop(if (stepNo < 0) 0 else stepNo).take(if (stepNo < 0) s.elements.size else 1))
    })
    form.copy(sections = sections.filter(s => !s.elements.isEmpty))
  }

  def getCreateFormRequest(formTask: Task[Form], isRefreshId: Boolean, sectionName: String, stepNo: Int): Task[CreateFormRequest] = {
    val r = for {
      f <- formTask
      newFormId = if (isRefreshId) Some(UUID.randomUUID()) else None
      fetchedElements <- {
        val id = f.id
        run(ElementQueries.getCreateElementRequestByFormId(id))
      }.dependOnDataSource().provide(dataSourceLayer)
      fetchedSections <- {
        //println(s"Fetched elements = $fetchedElements")
        ZIO.effect({
          val optsValidationsMap: Map[String, (List[Validations], List[Options])] = Map.empty
          var sectionMap: Map[(String, Int), List[CreateElementRequest]] = Map.empty
          def checkId(x: UUID, y: UUID) = x == y
          fetchedElements.map(x => {
            val optkey = x._1.sectionName.getOrElse("") + "_" + x._1.title
            val key = (x._1.sectionName.getOrElse(""), x._1.sectionSeqNo.getOrElse(0))
            sectionMap.get(key) match {
              case Some(xs) =>
                val ov = optsValidationsMap.get(optkey).getOrElse((List.empty, List.empty))
                val valids: List[Validations] = ov._1 ::: x._2.map(List(_)).getOrElse(List.empty)
                val opts: List[Options] = ov._2 ::: x._3.map(List(_)).getOrElse(List.empty)
                optsValidationsMap.updated(optkey, (valids, opts))
                val datas: List[CreateElementRequest] = {
                  val isFound = xs.exists(t => checkId(t.id, x._1.id))
                  val tmpxs = if (isFound) xs.map(t => if (checkId(t.id, x._1.id)) t.copy(options = t.options.map(tt => {tt.toList ::: opts}.distinct), validations = {t.validations.toList ::: valids}.distinct) else t) else xs ::: List(Element.toCreateElementRequest(x._1, valids, Some(opts)))
                  tmpxs
                }
                sectionMap = sectionMap + (key -> datas)
              case _ =>
                val valids = x._2.map(List(_)).getOrElse(List.empty)
                val opts = x._3.map(List(_)).getOrElse(List.empty)
                optsValidationsMap.updated(optkey, (valids, opts))
                val newElement: CreateElementRequest = Element.toCreateElementRequest(x._1, valids, Some(opts))
                sectionMap = sectionMap + (key -> List(newElement))
            }
          })
          val sections: Seq[FormSection] = sectionMap.keySet.toList.zipWithIndex.map(s => FormSection(s._1._1, Some(s._2 + 1), sectionMap.get(s._1).getOrElse(List.empty)))
          //println(s"Sections = $sections")
          sections
        })
      }
    } yield {
      val cf = Form.toCreateFormRequest(f, fetchedSections.toList, newFormId)
      if (isRefreshId) {applyIdChangesOnForm(cf, sectionName, stepNo)} else {
        if (stepNo > 0) {
          applySectionAndStepFilteringOnForm(cf, sectionName, stepNo)
        } else cf
      }
    }
    isRefreshId match {
      case true =>
        val finalForm = for {
          form <- r
          savedForm <- upsert(form.copy(formJson = Some(form.toJson), status = Some("pending"), metadata = form.metadata.map(m => m.copy(createdAt = Metadata.default.createdAt, updatedAt = None))), sectionName = "", stepNo = -1)
        } yield savedForm.copy(formJson = None)
        finalForm
      case _ =>
        r.map(_.copy(formJson = None))
    }
  }

  override def upsert(form: CreateFormRequest, sectionName: String, stepNo: Int = -1): Task[CreateFormRequest] = {
    val dbForm = CreateFormRequest.toForm(form, if (stepNo <= 0) Some("created") else form.status)
    //println(s"Form to be inserted or updated = $dbForm")
    val formTask = {
      transaction {
        for {
          existingForm <- getById(dbForm.id)
          //existingForm <- findById(dbForm.id, isRefreshId = false)
          id     <- {
            val updatedFormObj = if (existingForm.isDefined) dbForm.copy(metadata = existingForm.get.metadata) else dbForm
            run(FormQueries.upsert(updatedFormObj))
          }
          requestedElements = form.getFormElements()
          elements: Seq[Element] = requestedElements.map(CreateElementRequest.toElement(_)).zipWithIndex.map(e => e._1.copy(seqNo = Option(e._2 + 1), formId = Some(id)))
          savedElements <- {
            run(ElementQueries.batchUpsert(elements))
          }
          savedValids <- {
            val xs: Map[UUID, Seq[Validations]] = requestedElements.groupBy(_.id).map(v => (v._1, v._2.map(_.validations).flatten.map(x => x.copy(elementId = Some(v._1)))))
            run(ValidationsQueries.batchUpsert(xs.values.toSeq.flatten))
          }
          savedOpts <- {
            val options: Map[UUID, Seq[Options]] = requestedElements.groupBy(_.id).map(v => (v._1, v._2.map(_.options.getOrElse(Seq.empty)).flatten.zipWithIndex.map(x => x._1.copy(seqNo = Some(x._2 + 1), elementId = Some(v._1)))))
            run(OptionsQueries.batchUpsert(options.values.toSeq.flatten))
          }
          xs <- {
            run(FormQueries.byId(id))
          }
        } yield xs.headOption.getOrElse(throw new Exception("Insert or update failed!"))
      }.dependOnDataSource().provide(dataSourceLayer)
    }
    getCreateFormRequest(formTask, isRefreshId = false, sectionName, stepNo + 1)
  }

  override def all(owner: Option[String]): Task[Seq[Form]] = owner match {
    case Some(username) =>
      run(FormQueries.byOwner(username)).dependOnDataSource().provide(dataSourceLayer)
    case _ =>
      run(FormQueries.elementsQuery).dependOnDataSource().provide(dataSourceLayer)
  }

  def getById(id: UUID) = {
    val formTask = for {
      results <- run(FormQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
    } yield results.headOption
    formTask
  }


  override def findById(id: UUID, isRefreshId: Boolean): Task[CreateFormRequest] = {
    val formTask = for {
      results <- run(FormQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
      element    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find element with id $id", id.toString))
    } yield element
    getCreateFormRequest(formTask, isRefreshId, sectionName = "", stepNo = 0)
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

  override def deleteById(id: UUID): Task[Option[String]] = {
    val formTask = ctx.transaction {
      for {
        templateForm <- run(FormQueries.byTemplateId(id))
        deleteTemplate <- {
          if (templateForm.headOption.isDefined)
            run(FormQueries.deleteFormTemplate(id))
          else run(FormQueries.delete(id))
        }
        results <- {
            run(FormQueries.delete(id))
        }
        element <- ZIO.fromOption(Some("")).orElseFail(NotFoundException(s"Could not delete element with id $id", id.toString))
      } yield element
    }.dependOnDataSource().provide(dataSourceLayer)
    formTask.map(s => if (s.isEmpty) None else Some(s"Error in deleting record by id, ${id.toString}"))
  }

}


object FormQueries {

  import com.data2ui.repository.repository.FormContext._

  // NOTE - if you put the type here you get a 'dynamic query' - which will never wind up working...
  implicit val elementSchemaMeta = schemaMeta[Form](""""form"""")
  //implicit val elementInsertMeta = insertMeta[Form](_.id)

  def existsAny[T] = quote {
    (xs: Query[T]) => (p: T => Boolean) =>
      xs.filter(p(_)).nonEmpty
  }

  val elementsQuery                   = quote(query[Form])
  def byId(id: UUID)               = quote(elementsQuery.filter(_.id == lift(id)))
  def byTemplateId(id: UUID)       = quote(elementsQuery.filter(_.templateId == lift(Some(id): Option[UUID])))
  def delete(id: UUID)               = {
    quote(elementsQuery.filter(_.id == lift(id)).delete)
  }
  def deleteFormTemplate(id: UUID)               = {
    quote(elementsQuery.filter(_.templateId.isDefined).filter(_.templateId == lift(Some(id): Option[UUID])).delete)
    /*
    quote {
      query[Form].filter { v1 =>
        existsAny(query[Form])(v2 => (v1.templateId == lift(Some(id): Option[UUID])) && v2.templateId.isDefined)
      }.delete
    }
     */
  }
  def byOwner(username: String)               = quote(elementsQuery.filter(_.metadata.map(_.createdBy) == lift(Some(username): Option[String])))
  def byTitle(name: String)               = quote(elementsQuery.filter(_.title == lift(name)))
  def filter(values: Seq[FieldValue])               = quote(query[Form])
  def insertForm(element: Form) = quote(elementsQuery.insert(lift(element)))
  def upsertForm(element: Form) = quote(elementsQuery.update(lift(element)))
  def upsert(element: Form) = quote(elementsQuery.insert(lift(element)).onConflictUpdate(_.id)((t, e) => t.id -> e.id, (t, e) => t.tenantId -> e.tenantId, (t, e) => t.templateId -> e.templateId, (t, e) => t.title -> e.title, (t, e) => t.subTitle -> e.subTitle, (t, e) => t.status -> e.status, (t, e) => t.designProperties.map(_.width) -> e.designProperties.map(_.width), (t, e) => t.designProperties.map(_.height) -> e.designProperties.map(_.height),(t, e) => t.designProperties.map(_.fontFamily) -> e.designProperties.map(_.fontFamily),(t, e) => t.designProperties.map(_.backgroundColor) -> e.designProperties.map(_.backgroundColor), (t, e) => t.designProperties.map(_.textColor) -> e.designProperties.map(_.textColor), (t, e) => t.metadata.map(_.updatedAt) -> e.metadata.map(_.updatedAt), (t, e) => t.metadata.map(_.updatedBy) -> e.metadata.map(_.updatedBy)).returning(_.id))
}
