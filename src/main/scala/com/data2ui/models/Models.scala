package com.data2ui.models

import com.bootes.dao.{CreateUserRequest, Metadata, User}
import io.getquill.Embedded
import io.scalaland.chimney.dsl.TransformerOps
import zio.json.{DeriveJsonCodec, JsonCodec, JsonDecoder, JsonEncoder}
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
                      seqNo: Option[Int],
                      name: String = "message",
                      title: String,
                      description: Option[String],
                      values: Seq[String],
                      `type`: String = "String",
                      required: Option[Boolean] = Option(true),
                      customerError: Option[String] = None,
                      errors: Option[Seq[String]] = None,
                      //options: Option[Seq[Options]] = None,
                      optionsType: Option[String] = None,
                      //validations: Seq[Validations],
                      sectionName: Option[String],
                      sectionSeqNo: Option[Int],
                      config: Option[Config] = Option(Config(delayInSeconds = 1, showProgressBar = true, progressBarUri = None)),
                      action: Option[Boolean] = Option(false),
                      status: Option[String] = Option("active"),
                      metadata: Option[Metadata] = None,
                      formId: Option[UUID]
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


  case class FormSection(title: String, seqNo: Option[Int], elements: Seq[CreateElementRequest]) {
    def makeElementsOrdered(): Seq[CreateElementRequest] = elements.zipWithIndex.map(e => e._1.copy(sectionName = Option(title), seqNo = Some(e._2)))
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
                                metadata: Option[Metadata] = Some(Metadata.default)
                              ) {
    def getFormElements() = sections.zipWithIndex.map(s => s._1.elements.map(_.copy(sectionName = Some(s._1.title), sectionSeqNo = Some(s._2 + 1)))).flatten
  }
  object CreateFormRequest{
    implicit val codec: JsonCodec[CreateFormRequest] = DeriveJsonCodec.gen[CreateFormRequest]

    implicit def toForm(record: CreateFormRequest): Form = record.into[Form].transform.copy(id = record.id, metadata = record.metadata)
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
                   metadata: Option[Metadata] = None
                 )
  object Form {
    implicit val codec: JsonCodec[Form] = DeriveJsonCodec.gen[Form]

    implicit def toCreateFormRequest(record: Form, sections: List[FormSection], newFormId: Option[UUID]): CreateFormRequest = {
      val isRefreshId = newFormId.isDefined
      CreateFormRequest(id = if (!isRefreshId) record.id else newFormId.getOrElse(UUID.randomUUID()), tenantId = record.tenantId, requestId = if (!isRefreshId) record.requestId else Some(UUID.randomUUID()), templateId = record.templateId, title = record.title,
        subTitle = record.subTitle, sections = sections, designProperties = record.designProperties, status = record.status, metadata = record.metadata)
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
}
