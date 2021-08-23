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
                           id: Long,
                           `type`: String,
                           title: String,
                           minimum: Option[Int],
                           maximum: Option[Int],
                           values: Option[Seq[String]],
                           elementId: Option[Long]
                         )
  object Validations {
    implicit val codec: JsonCodec[Validations] = DeriveJsonCodec.gen[Validations]
  }

  case class Options (
                       id: Long,
                       value: String,
                       text: String,
                       elementId: Option[Long]
                     )
  object Options {
    implicit val codec: JsonCodec[Options] = DeriveJsonCodec.gen[Options]
  }

  sealed trait IElement extends Embedded {
    def tenantId: Long
    def id: Long
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
                      id: Long,
                      tenantId: Long,
                      seqNo: Int,
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
                      config: Option[Config] = Option(Config(delayInSeconds = 1, showProgressBar = true, progressBarUri = None)),
                      action: Option[Boolean] = Option(false),
                      status: Option[String] = Option("active"),
                      metadata: Option[Metadata] = None,
                      formId: Option[Long]
                    ) extends IElement
  object Element {
    implicit val codec: JsonCodec[Element] = DeriveJsonCodec.gen[Element]
  }

  case class CreateElementRequest(
                                   id: Long,
                                   tenantId: Long,
                                   seqNo: Int,
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
                                   config: Option[Config] = Option(Config(delayInSeconds = 1, showProgressBar = true, progressBarUri = None)),
                                   action: Option[Boolean] = Option(false),
                                   metadata: Option[Metadata] = Some(Metadata.default),
                                   formId: Option[Long]
                                 ) {
  }
  object CreateElementRequest{
    implicit val codec: JsonCodec[CreateElementRequest] = DeriveJsonCodec.gen[CreateElementRequest]
    def toElement(element: CreateElementRequest) = element.into[Element].transform.copy(id = element.id, tenantId = element.tenantId, seqNo = element.seqNo)
  }

  /*
  case class Message(
    id: Long,
    tenantId: Long,
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
                    config: Option[Config] = Option(Config(delayInSeconds = 1, showProgressBar = true, progressBarUri = None)),
                    action: Option[Boolean] = Option(false),
                  ) extends IElement
  object Message {
    implicit val codec: JsonCodec[Message] = DeriveJsonCodec.gen[Message]
  }

  case class Input(
                    id: Long,
                    tenantId: Long,
                    name: String,
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
                           config: Option[Config] = None,
                           action: Option[Boolean] = Option(false),
                         ) extends IElement
  object Input {
    implicit val codec: JsonCodec[Input] = DeriveJsonCodec.gen[Input]
  }

  case class Textarea(
                       id: Long,
                       tenantId: Long,
                    name: String = "textarea",
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
                    config: Option[Config] = None,
                    action: Option[Boolean] = Option(false),
                  ) extends IElement
  object Textarea {
    implicit val codec: JsonCodec[Textarea] = DeriveJsonCodec.gen[Textarea]
  }

  case class Selection(
                        tenantId: Long,
                        id: Long,
                       name: String = "select",
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
                       config: Option[Config] = None,
                       action: Option[Boolean] = Option(false),
                     ) extends IElement
  object Selection {
    implicit val codec: JsonCodec[Selection] = DeriveJsonCodec.gen[Selection]
  }

  case class UserAction(
                         id: Long,
                         tenantId: Long,
                        name: String = "action",
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
                        config: Option[Config] = None,
                        action: Option[Boolean] = Option(true),
                      ) extends IElement
  object UserAction {
    implicit val codec: JsonCodec[UserAction] = DeriveJsonCodec.gen[UserAction]
  }
*/

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


  case class FormSection(title: String, elements: Seq[CreateElementRequest]) {
    def makeElementsOrdered(): Seq[CreateElementRequest] = elements.zipWithIndex.map(e => e._1.copy(sectionName = Option(title), seqNo = e._2))
  }
  object FormSection{
    implicit val codec: JsonCodec[FormSection] = DeriveJsonCodec.gen[FormSection]
  }
  case class CreateFormRequest(
                                id: Long,
                                requestId: Option[String],
                                uid: String,
                                title: String,
                                subTitle: Option[String],
                                sections: Seq[FormSection],
                                designProperties: Option[DesignProperties],
                                status: Option[String]
                              ) {
    def getFormElements() = sections.map(_.elements).flatten
  }
  object CreateFormRequest{
    implicit val codec: JsonCodec[CreateFormRequest] = DeriveJsonCodec.gen[CreateFormRequest]

    implicit def toForm(record: CreateFormRequest): Form = record.into[Form].transform.copy(id = record.id, metadata = Some(Metadata.default))
  }

  case class Form(
                   id: Long = -1,
                   requestId: Option[String] = None,
                   uid: String,
                   title: String,
                   subTitle: Option[String],
                   designProperties: Option[DesignProperties] = None,
                   status: Option[String] = None,
                   metadata: Option[Metadata] = None
                 )
  object Form {
    implicit val codec: JsonCodec[Form] = DeriveJsonCodec.gen[Form]
  }

  /*
  case class UiForm (
                   id: Long = -1,
                   requestId: Option[String],
                   uid: String,
                   title: String,
                   subTitle: Option[String],
                   designProperties: Option[DesignProperties],
                   elements: List[Element],
                   status: Option[String],
                   metadata: Option[Metadata] = None
                 )
  object UiForm {
    implicit val codec: JsonCodec[UiForm] = DeriveJsonCodec.gen[UiForm]
  }
   */
  case class UiRequest(requestId: String, data: List[Element])
  object UiRequest {
    implicit val codec: JsonCodec[UiRequest] = DeriveJsonCodec.gen[UiRequest]
  }

  case class UiResponse(requestId: String, status: Boolean, message: String, code: String, data: List[Element])
  object UiResponse{
    implicit val codec: JsonCodec[UiResponse] = DeriveJsonCodec.gen[UiResponse]
  }
}
