package com.data2ui.models

import com.bootes.dao.Metadata
import io.getquill.Embedded
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
                           elementId: Long
                         )
  object Validations {
    implicit val codec: JsonCodec[Validations] = DeriveJsonCodec.gen[Validations]
  }

  case class Options (
                       id: Long,
                       value: String,
                       text: String,
                       elementId: Long
                     )
  object Options {
    implicit val codec: JsonCodec[Options] = DeriveJsonCodec.gen[Options]
  }

  sealed trait IElement extends Embedded {
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
                      config: Option[Config] = Option(Config(delayInSeconds = 1, showProgressBar = true, progressBarUri = None)),
                      action: Option[Boolean] = Option(false),
                      status: Option[String] = Option("active"),
                      metadata: Option[Metadata] = None
                    ) extends IElement
  object Element {
    implicit val codec: JsonCodec[Element] = DeriveJsonCodec.gen[Element]
  }

  case class Message(
    id: Long,
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


  case class UiRequest(requestId: String, data: List[Element])
  object UiRequest {
    implicit val codec: JsonCodec[UiRequest] = DeriveJsonCodec.gen[UiRequest]
  }

  case class UiResponse(requestId: String, status: Boolean, message: String, code: String, data: List[Element])
  object UiResponse{
    implicit val codec: JsonCodec[UiResponse] = DeriveJsonCodec.gen[UiResponse]
  }
}
