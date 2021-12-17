package com.data2ui.models

import com.bootes.dao.{CreateUserRequest, Metadata, User}
import com.bootes.validators.Validators
import io.getquill.Embedded
import io.scalaland.chimney.dsl.TransformerOps
import zio.json.{DeriveJsonCodec, EncoderOps, JsonCodec, JsonDecoder, JsonEncoder}
import zio.macros.accessible
import zio.prelude.{SubtypeSmart, Validation}
import zio.test.Assertion
import zio.test.Assertion._

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Models {

  case class Config(
                     delayInSeconds: Int,
                     showProgressBar: Boolean,
                     progressBarUri: Option[String] = None
                   ) extends Embedded
  object Config {
    implicit val codec: JsonCodec[Config] = DeriveJsonCodec.gen[Config]
  }

  case class Validations (
                           id: UUID,
                           `type`: String,
                           title: String,
                           minimum: Option[Int],
                           maximum: Option[Int],
                           values: Option[Seq[String]],
                           elementId: Option[UUID]
                         )
  object Validations {
    implicit val codec: JsonCodec[Validations] = DeriveJsonCodec.gen[Validations]
  }

  case class Options (
                       id: UUID,
                       value: String,
                       text: String,
                       seqNo: Option[Int] = None,
                       elementId: Option[UUID]
                     )
  object Options {
    implicit val codec: JsonCodec[Options] = DeriveJsonCodec.gen[Options]
  }

  sealed trait IElement extends Embedded {
    def id: UUID
    def name: String
    def title: String
    def description: Option[String]
    def values: Seq[String]
    def `type`: String
    def required: Option[Boolean]
    def customerError: Option[String]
    def errors: Option[Seq[String]]
    //def options: Option[Seq[Options]]
    def optionsType: Option[String]
    //def validations: Seq[Validations]
    def config: Option[Config]
    def action: Option[Boolean]
  }
  object IElement {
    implicit val codec: JsonCodec[IElement] = DeriveJsonCodec.gen[IElement]
  }

  case class Element(
                      id: UUID,
                      seqNo: Option[Int] = None,
                      name: String = "message",
                      title: String = "",
                      description: Option[String] = None,
                      values: Seq[String] = Seq.empty,
                      `type`: String = "String",
                      required: Option[Boolean] = Option(true),
                      customerError: Option[String] = None,
                      errors: Option[Seq[String]] = None,
                      //options: Option[Seq[Options]] = None,
                      optionsType: Option[String] = None,
                      //validations: Seq[Validations],
                      sectionName: Option[String] = None,
                      sectionSeqNo: Option[Int] = None,
                      config: Option[Config] = Option(Config(delayInSeconds = 1, showProgressBar = true, progressBarUri = None)),
                      action: Option[Boolean] = Option(false),
                      status: Option[String] = Option("active"),
                      metadata: Option[Metadata] = None,
                      formId: Option[UUID] = None
                    ) extends IElement
  object Element {
    implicit val codec: JsonCodec[Element] = DeriveJsonCodec.gen[Element]

    def getId() = UUID.randomUUID()

    def toCreateElementRequest(element: Element, valids: Seq[Validations], opts: Option[Seq[Options]]) = {
      CreateElementRequest(id = element.id, seqNo = element.seqNo, name = element.name, title = element.title, description = element.description,
        values = element.values, `type` = element.`type`, required = element.required,
        customerError = element.customerError, errors = element.errors, options = opts,
        optionsType = element.optionsType, validations = valids, sectionName = element.sectionName, sectionSeqNo = element.sectionSeqNo,
        config = element.config, action = element.action,  metadata = element.metadata,
        formId = element.formId)
    }
  }

  case class CreateElementRequest(
                                   id: UUID,
                                   seqNo: Option[Int],
                                   name: String = "message",
                                   title: String,
                                   description: Option[String],
                                   values: Seq[String],
                                   `type`: String = "String",
                                   required: Option[Boolean] = Option(true),
                                   customerError: Option[String] = None,
                                   errors: Option[Seq[String]] = None,
                                   options: Option[Seq[Options]] = None,
                                   optionsType: Option[String] = None,
                                   validations: Seq[Validations],
                                   sectionName: Option[String],
                                   sectionSeqNo: Option[Int],
                                   config: Option[Config] = Option(Config(delayInSeconds = 1, showProgressBar = true, progressBarUri = None)),
                                   action: Option[Boolean] = Option(false),
                                   metadata: Option[Metadata] = Some(Metadata.default),
                                   formId: Option[UUID]
                                 ) {
  }
  object CreateElementRequest{
    implicit val codec: JsonCodec[CreateElementRequest] = DeriveJsonCodec.gen[CreateElementRequest]
    def toElement(element: CreateElementRequest) = element.into[Element].transform.copy(id = element.id, seqNo = element.seqNo)
    def makeFileElement(id: UUID, formId: UUID) = CreateElementRequest(id = id, seqNo = None, name = "file", title = "", description = None,
      values = Seq.empty, `type` = "String", formId = Some(formId), validations = Seq.empty, sectionName = None, sectionSeqNo = None)
  }

  case class DesignProperties (
                                width: Option[Int],
                                height: Option[Int],
                                backgroundColor: Option[String],
                                textColor: Option[String],
                                fontFamily: Option[String]
                              ) extends Embedded
  object DesignProperties{
    implicit val codec: JsonCodec[DesignProperties] = DeriveJsonCodec.gen[DesignProperties]
  }


  case class FormSection(title: String, seqNo: Option[Int], elements: Seq[CreateElementRequest], customerError: Option[String] = None) {
    def makeElementsOrdered(): Seq[CreateElementRequest] = elements.zipWithIndex.map(e => e._1.copy(sectionName = Option(title), seqNo = if (e._1.seqNo.isDefined && e._1.seqNo.getOrElse(0) > 0) e._1.seqNo else Some(e._2)))
  }
  object FormSection{
    implicit val codec: JsonCodec[FormSection] = DeriveJsonCodec.gen[FormSection]
  }
  case class CreateFormRequest(
                                id: UUID,
                                tenantId: Int,
                                requestId: Option[UUID],
                                templateId: Option[UUID],
                                title: String,
                                subTitle: Option[String],
                                sections: Seq[FormSection],
                                designProperties: Option[DesignProperties],
                                status: Option[String],
                                formJson: Option[String] = None,
                                metadata: Option[Metadata] = Some(Metadata.default),
                                customerError: Option[String] = None,
                                errors: Option[Seq[String]] = None
                              ) {
    def getFormElements() = sections.zipWithIndex.map(s => s._1.elements.map(_.copy(sectionName = Some(s._1.title), sectionSeqNo = if (s._1.seqNo.isDefined) s._1.seqNo else Some(s._2 + 1)))).flatten

    def hasErrors = (customerError.isDefined && !customerError.getOrElse("").isEmpty) || (sections.filter(s => !s.elements.filter(e => !e.customerError.getOrElse("").isEmpty).isEmpty).nonEmpty)
  }
  object CreateFormRequest{
    implicit val codec: JsonCodec[CreateFormRequest] = DeriveJsonCodec.gen[CreateFormRequest]

    implicit def toForm(record: CreateFormRequest, status: Option[String]): Form = {
      record.into[Form].transform.copy(id = record.id, metadata = record.metadata).copy(status = status)
    }
    def validate(value: CreateFormRequest): CreateFormRequest = {
      val v1 = Validators.validateTitle(value.title).toEither
      if (v1.isLeft) value.copy(customerError = Some(v1.left.get.mkString(", "))) else {
          val v = Validators.validateFormSections(value.sections).toEither
          if (v.isLeft) value.copy(customerError = Some(v.left.get.mkString(", "))) else value.copy(sections = v.right.get)
      }
    }
  }

  case class Form(
                   id: UUID,
                   tenantId: Int,
                   requestId: Option[UUID] = None,
                   templateId: Option[UUID] = None,
                   title: String,
                   subTitle: Option[String],
                   designProperties: Option[DesignProperties] = None,
                   status: Option[String] = None,
                   formJson: Option[String] = None,
                   metadata: Option[Metadata] = None
                 ) {
  }
  object Form {
    implicit val codec: JsonCodec[Form] = DeriveJsonCodec.gen[Form]

    implicit def toCreateFormRequest(record: Form, sections: List[FormSection], newFormId: Option[UUID]): CreateFormRequest = {
      val isRefreshId = newFormId.isDefined
      CreateFormRequest(id = if (!isRefreshId) record.id else newFormId.getOrElse(UUID.randomUUID()), tenantId = record.tenantId, requestId = if (!isRefreshId) record.requestId else Some(UUID.randomUUID()), templateId = if (isRefreshId) Some(record.id) else record.templateId, title = record.title,
        subTitle = record.subTitle, sections = sections, designProperties = record.designProperties, status = record.status, formJson = record.formJson, metadata = record.metadata)
    }
  }

  case class UiRequest(requestId: String, data: List[Element])
  object UiRequest {
    implicit val codec: JsonCodec[UiRequest] = DeriveJsonCodec.gen[UiRequest]
  }

  case class UiResponse(requestId: String, status: Boolean, message: String, code: String, data: List[Element])
  object UiResponse{
    implicit val codec: JsonCodec[UiResponse] = DeriveJsonCodec.gen[UiResponse]
  }

  case class UploadResponse(id: Option[UUID], message: String, filename: String, path: Option[String] = None)
  object UploadResponse {
    implicit val codec: JsonCodec[UploadResponse] = DeriveJsonCodec.gen[UploadResponse]
  }

}
