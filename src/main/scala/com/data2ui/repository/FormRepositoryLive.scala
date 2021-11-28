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

  def applyIdChangesOnElements(elt: CreateElementRequest): CreateElementRequest = {
        val eid = UUID.randomUUID()
        elt.copy(id = eid, validations = elt.validations.map(_.copy(id = UUID.randomUUID(), elementId = Some(eid))), options = elt.options.map(opts => opts.map(_.copy(id = UUID.randomUUID(), elementId = Some(eid)))))
  }

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

  def getCreateFormRequest(formTask: Task[Form], isRefreshId: Boolean, sectionName: String, stepNo: Int, inputFormId: Option[UUID] = None): Task[CreateFormRequest] = {
    //println(s"getCreateFormRequest: isRefreshId = $isRefreshId, sectionName = $sectionName and stepNo = $stepNo\n\n\n")
    val r = for {
      f <- formTask
      isVisitorSubmittedForm = f.templateId.isDefined && (stepNo > 0)
      newFormId = if (isRefreshId) Some(UUID.randomUUID()) else None
      selectedElements <- {
        val id = f.id
        run(ElementQueries.byFormIdWithOrdering(id))
      }.dependOnDataSource().provide(dataSourceLayer)
      selectedValidations <- {
        run(ValidationsQueries.filterByElementIds(selectedElements.map(_.id)))
      }.dependOnDataSource().provide(dataSourceLayer)
      selectedOptions <- {
        run(OptionsQueries.filterByElementIds(selectedElements.map(i => Option(i.id))))
      }.dependOnDataSource().provide(dataSourceLayer)
      //fetchedElements <- {
      //  val id = f.id
      //  run(ElementQueries.getCreateElementRequestByFormId(id))
      //}.dependOnDataSource().provide(dataSourceLayer)
      fetchedElements = {
        selectedElements.toList.map(e => {
          (e,
            Some(selectedValidations.toList.filter(v => v.elementId.map(id => id.toString.equals(e.id.toString)).getOrElse(false))),
            Some(selectedOptions.toList.filter(v => v.elementId.map(id => id.toString.equals(e.id.toString)).getOrElse(false)))
          )
        })
        //Seq((selectedElements.toList, selectedValidations.toList, selectedOptions.toList))
      }
      templateFormFetchedElementsIds <- {
        val id = if (isVisitorSubmittedForm) f.templateId.getOrElse(UUID.randomUUID) else UUID.randomUUID
        run(ElementQueries.byFormIdWithOrdering(id))
        //run(ElementQueries.getCreateElementRequestByFormId(id))
      }.dependOnDataSource().provide(dataSourceLayer)
      selectedValidations2 <- {
        run(ValidationsQueries.filterByElementIds(templateFormFetchedElementsIds.map(_.id)))
      }.dependOnDataSource().provide(dataSourceLayer)
      selectedOptions2 <- {
        run(OptionsQueries.filterByElementIds(templateFormFetchedElementsIds.map(i => Option(i.id))))
      }.dependOnDataSource().provide(dataSourceLayer)
      templateFormFetchedElements = {
        templateFormFetchedElementsIds.toList.map(e => {
          (e,
            Some(selectedValidations2.toList.filter(v => v.elementId.map(id => id.toString.equals(e.id.toString)).getOrElse(false))),
            Some(selectedOptions2.toList.filter(v => v.elementId.map(id => id.toString.equals(e.id.toString)).getOrElse(false)))
          )
        })
        //Seq((selectedElements.toList, selectedValidations.toList, selectedOptions.toList))
      }
      fetchedSections <- {
        //println(s"Fetched elements = $fetchedElements")
        val previousStepNo = stepNo - 1
        val oldElements = if (stepNo > 0) fetchedElements.filter(tup => (previousStepNo > 0) && (tup._1.seqNo.getOrElse(-1) == previousStepNo)).distinctBy(e => e._1.id.toString) else fetchedElements.distinctBy(e => e._1.id.toString)
        val oldIds = oldElements.map(_._1.id.toString)
        val newElements = templateFormFetchedElements.filter(tup => (tup._1.seqNo.getOrElse(-1) == stepNo) && (!oldElements.contains(tup._1.id.toString))).distinctBy(e => e._1.id.toString)
        val newIds = newElements.map(_._1.id.toString)
        val finalElements = oldElements ++ newElements
        //println(s"finalElements = $finalElements")
        val sectionNoMap = finalElements.map(fe => (fe._1.sectionName.getOrElse(""), fe._1.sectionSeqNo)).toMap
        ZIO.effect({
          val optsValidationsMap: Map[String, (List[Validations], List[Options])] = Map.empty
          var sectionMap: Map[(String, Int), List[CreateElementRequest]] = Map.empty
          def checkId(x: UUID, y: UUID) = x == y
          //println(s"No of elements found = ${oldElements.size}, finalElements size = ${finalElements.size}, oldIds = $oldIds, newIds = $newIds, newElements size = ${newElements.size} with isVisitorSubmittedForm = $isVisitorSubmittedForm\n\n")
          finalElements.map(x => {
            val optkey = x._1.sectionName.getOrElse("") + "_" + x._1.id.toString
            val key = (x._1.sectionName.getOrElse(""), x._1.sectionSeqNo.getOrElse(0))
            //println(s"optkey = $optkey and key = $key")
            sectionMap.get(key) match {
              case Some(xs) =>
                val ov = optsValidationsMap.get(optkey).getOrElse((List.empty, List.empty))
                val valids: List[Validations] = ov._1 ::: x._2.map(List(_).flatten).getOrElse(List.empty)
                val opts: List[Options] = ov._2 ::: x._3.map(List(_).flatten).getOrElse(List.empty)
                //println(s"Found: opts = $opts for optkey = $optkey")
                optsValidationsMap.updated(optkey, (valids, opts))
                val datas: List[CreateElementRequest] = {
                  val isFound = xs.exists(t => checkId(t.id, x._1.id))
                  val tmpxs = if (isFound) xs.map(t => if (checkId(t.id, x._1.id)) t.copy(options = t.options.map(tt => {tt.toList ::: opts}.distinct), validations = {t.validations.toList ::: valids}.distinct) else t) else {
                    val noOfElements = xs.size
                    //println(s"noOfElements: $noOfElements and seqNo ele = ${x._1.seqNo}\n")
                    xs ::: List(Element.toCreateElementRequest(x._1.copy(seqNo = Some(noOfElements + 1)), valids, Some(opts)))
                  }
                  tmpxs
                }
                ////println(s"datas ids = ${datas.map(_.id)}")
                val isIdRefreshNeeded = newIds.contains(x._1.id.toString) && (stepNo > 0)
                val newElement: CreateElementRequest = Element.toCreateElementRequest(x._1, valids, Some(opts))
                val elt = if (!isIdRefreshNeeded) newElement else applyIdChangesOnElements(newElement)
                //println(s"Found: key = $key, isIdRefreshNeeded = $isIdRefreshNeeded and new elt = $elt for id = ${elt.id}\n")
                sectionMap = sectionMap + (key -> datas)
              case _ =>
                val valids = x._2.map(List(_)).getOrElse(List.empty)
                val opts = x._3.map(List(_)).getOrElse(List.empty)
                //println(s"Options = ${x._3}")
                //println(s"Not found: opts = $opts for optkey = $optkey")
                optsValidationsMap.updated(optkey, (valids, opts))
                val isIdRefreshNeeded = newIds.contains(x._1.id.toString) && (stepNo > 0)
                val newElement: CreateElementRequest = Element.toCreateElementRequest(x._1, valids.flatten, Some(opts.flatten))
                val elt = if (!isIdRefreshNeeded) newElement else applyIdChangesOnElements(newElement)
                //println(s"NotFound: key = $key, isIdRefreshNeeded = $isIdRefreshNeeded and new elt = $elt for id = ${elt.id}\n")
                sectionMap = sectionMap + (key -> List(elt))
            }
          })
          val sections: Seq[FormSection] = sectionMap.keySet.toList.zipWithIndex.map(s => {
            val no: Int = if (isVisitorSubmittedForm && (stepNo > 1)) sectionNoMap.get(s._1._1).getOrElse(None).getOrElse(s._2) else s._2 + 1
            FormSection(s._1._1, Some(no), sectionMap.get(s._1).getOrElse(List.empty))
          })
          //println(s"Sections = $sections\n\n")
          sections
        })
      }
    } yield {
      ////println(s"fetchedSections = ${fetchedSections.toList} \nwith fetchedSections = $fetchedSections, \nsectionName = $sectionName and stepNo = $stepNo")
      //println(s"\nsectionName = $sectionName and stepNo = $stepNo with isRefreshId = $isRefreshId")
      val cf = Form.toCreateFormRequest(f, fetchedSections.toList, newFormId)
      if (isRefreshId) {applyIdChangesOnForm(cf, sectionName, stepNo)} else {
        if (stepNo > 0) {
          if (isVisitorSubmittedForm)
            cf
          else
            applySectionAndStepFilteringOnForm(cf, sectionName, stepNo)
        } else cf
      }
    }
    isRefreshId match {
      case true =>
        val finalForm = for {
          form <- r
          savedForm <- {
            if (inputFormId.isDefined) 
              Task.succeed(form)
            else
              upsert(form.copy(formJson = Some(form.toJson), status = Some("pending"), metadata = form.metadata.map(m => m.copy(createdAt = Metadata.default.createdAt, updatedAt = None))), sectionName = "", stepNo = if (stepNo >= 0) stepNo -1 else stepNo)
          }
        } yield savedForm.copy(formJson = None)
        finalForm
      case _ =>
        r.map(_.copy(formJson = None))
    }
  }

  override def upsert(form: CreateFormRequest, sectionName: String, stepNo: Int = -1): Task[CreateFormRequest] = {
    val dbForm = CreateFormRequest.toForm(form, if (stepNo <= 0) Some("created") else form.status)
    val isTemplateForm = !form.templateId.isDefined
    //println(s"Form to be inserted or updated = $dbForm and \nform = $form with \nisTemplateForm = $isTemplateForm\n\n")
    val formTask = {
      transaction {
        for {
          existingForm <- getById(dbForm.id)
          //existingForm <- findById(dbForm.id, isRefreshId = false)
          isFormExisting = existingForm.isDefined
          id     <- {
            val updatedFormObj = if (isFormExisting) dbForm.copy(metadata = existingForm.get.metadata) else dbForm
            //println(s"isFormExisting = $isFormExisting with existing form data = $existingForm with stepNo = $stepNo for id, ${dbForm.id} and updatedFormObj = $updatedFormObj\n\n")
            if (isTemplateForm) {
                deleteById(dbForm.id) *> run(FormQueries.upsert(updatedFormObj))
            } else run(FormQueries.upsert(updatedFormObj))
          }
          requestedElements = form.getFormElements()
          
          isSubmissionCase = form.status.getOrElse("").equalsIgnoreCase("submitted")
          isSingleElement = requestedElements.size == 1
          tmpElements = if (isSubmissionCase && isSingleElement) {
            //println(s"isSubmissionCase = $isSubmissionCase and isSingleElement = $isSingleElement")
            val secElementsMap = form.sections.map(s => (s.title, s.seqNo)).toMap
            val inputFormElements = form.sections.map(_.elements).flatten.map(s => (s.id, s)).toMap
            requestedElements.map(CreateElementRequest.toElement(_)).map(e => {
              val sno = inputFormElements.get(e.id).map(s => s.seqNo.getOrElse(0)).getOrElse(0)
              val secno = inputFormElements.get(e.id).map(s => s.sectionSeqNo.getOrElse(0)).getOrElse(0)
              //println(s"sno = $sno and secno = $secno for elt id = ${e.id}")
              e.copy(seqNo = if (sno > 0) Some(sno) else e.seqNo, sectionSeqNo = if (secno > 0) Some(secno) else e.sectionSeqNo)
            })
          } else {
            //println(s"tmpElements else case: ${requestedElements.map(e => (e.id, e.seqNo))}")
            requestedElements.map(CreateElementRequest.toElement(_))
          }
          //elements: Seq[Element] = tmpElements.zipWithIndex.map(e => e._1.copy(seqNo = if (e._1.seqNo.isDefined && (e._1.seqNo.getOrElse(-1) > 1))  e._1.seqNo else Option(e._2 + 1), formId = Some(id)))
          elements: Seq[Element] = tmpElements.zipWithIndex.map(e => e._1.copy(seqNo = if (isSubmissionCase) {if (e._1.seqNo.isDefined && (e._1.seqNo.getOrElse(-1) > 1))  e._1.seqNo else Option(e._2 + 1)} else Option(e._2 + 1), formId = Some(id)))
          savedElements <- {
            //println(s"elements = $elements\n\n")
            run(ElementQueries.batchUpsert(elements))
          }
          savedValids <- {
            val xs: Map[UUID, Seq[Validations]] = requestedElements.groupBy(_.id).map(v => (v._1, v._2.map(_.validations).flatten.map(x => x.copy(elementId = Some(v._1)))))
            run(ValidationsQueries.batchUpsert(xs.values.toSeq.flatten))
          }
          savedOpts <- {
            val options: Map[UUID, Seq[Options]] = requestedElements.groupBy(_.id).map(v => (v._1, v._2.map(_.options.getOrElse(Seq.empty)).flatten.zipWithIndex.map(x => x._1.copy(seqNo = if (x._1.seqNo.isDefined && (x._1.seqNo.getOrElse(-1) > 1))  x._1.seqNo else Some(x._2 + 1), elementId = Some(v._1)))))
            run(OptionsQueries.batchUpsert(options.values.toSeq.flatten))
          }
          xs <- {
            //println(s"savedElements = $savedElements, savedValids = $savedValids and savedOpts = $savedOpts for form id = $id")
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


  override def findById(id: UUID, isRefreshId: Boolean, seqNo: Int): Task[CreateFormRequest] = {
    val formTask = for {
      results <- run(FormQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
      element    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find element with id $id", id.toString))
    } yield element
    getCreateFormRequest(formTask, isRefreshId, sectionName = "", stepNo = seqNo, inputFormId = if (isRefreshId) Some(id) else None)
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

  override def uploadFile(id: UUID, formId: Option[UUID], filename: Option[String]): Task[CreateElementRequest] = {
    println(s"uploadFile: id = $id, formId = $formId and filename = $filename")
    for {
      results <- run(ElementQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
      r <- {
        val elt = Element.toCreateElementRequest(results.headOption.get, Seq.empty, None).copy(values = Seq(filename.getOrElse("")))
        Task.succeed(elt)
      }
      xs <- ZIO.effect(r).orElseFail(NotFoundException(s"Could not find elements with input criteria", id.toString))
    } yield xs
  }

  override def uploadFile(element: CreateElementRequest): Task[CreateElementRequest] = {
    val elt = CreateElementRequest.toElement(element)
    for {
      results <- run(ElementQueries.upsert(elt)).dependOnDataSource().provide(dataSourceLayer)
      xs <- ZIO.effect(element).orElseFail(NotFoundException(s"Could save uploaded record with id, ", element.id.toString))
    } yield xs
  }

  override def deleteById(id: UUID): Task[Option[String]] = {
    val formTask = ctx.transaction {
      for {
        templateForm <- run(FormQueries.byTemplateId(id))
        form <- run(FormQueries.byId(id))
        isTemplateExists = templateForm.headOption.isDefined
        isFormExists = form.headOption.isDefined
        deleteTemplate <- {
          if (isTemplateExists)
            run(FormQueries.deleteFormTemplate(id))
          else run(FormQueries.delete(id))
        }
        results <- {
          run(FormQueries.delete(id))
        }
        element <- {
          ZIO.fromOption(if (isTemplateExists || isFormExists) Some("") else Some(s"Not found the record by id, ${id.toString}")).orElseFail(NotFoundException(s"Could not delete record with id $id", id.toString))
        }
      } yield element
    }.dependOnDataSource().provide(dataSourceLayer)
    formTask.map(s => if (s.isEmpty) None else Some(s))
  }


  override def deleteTemplateForm(id: UUID, forced: Option[Boolean]): Task[Option[String]] = {
    val formTask = ctx.transaction {
      for {
        templateForm <- run(FormQueries.byTemplateId(id))
        updatedTemplateForm <- {
          if (templateForm.headOption.isDefined) {
            if (forced.getOrElse(false)) {
              run(FormQueries.upsert(templateForm.headOption.get.copy(templateId = None)))
            } else {
              ZIO.effectTotal(IO.fail(new Exception(s"Deletion of form template cannot be done because there are form submissions existing.")))
            }
          } else run(FormQueries.byTemplateId(id))
        }
        form <- run(FormQueries.byId(id))
        isFormExists = form.headOption.isDefined
        results <- {
            run(FormQueries.delete(id))
        }
        element <- {
          ZIO.fromOption(if (isFormExists) Some("") else Some(s"Not found the record by id, ${id.toString}")).orElseFail(NotFoundException(s"Could not delete record with id $id", id.toString))
        }
      } yield element
    }.dependOnDataSource().provide(dataSourceLayer)
    formTask.map(s => if (s.isEmpty) None else Some(s))
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
