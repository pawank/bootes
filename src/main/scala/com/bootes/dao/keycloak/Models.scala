package com.bootes.dao.keycloak

import org.apache.commons.validator.routines.EmailValidator
import zio.console.Console
import zio.json.{DeriveJsonCodec, JsonCodec, JsonDecoder, JsonEncoder}
import zio.macros.accessible
import zio.prelude.{SubtypeSmart, Validation}
import zio.test.Assertion
import zio.test.Assertion._

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Models {
  val emailValidator:EmailValidator = EmailValidator.getInstance()

  /*
  Examples satisfying phone nos
  123-456-7890
(123) 456-7890
123 456 7890
123.456.7890
+91 (123) 456-7890
  object Phone extends zio.prelude.Subtype[String] {
    implicit val encoder: JsonEncoder[Phone.Type] = JsonEncoder[String].contramap(Phone.unwrap)
    implicit val decoder: JsonDecoder[Phone.Type] = JsonDecoder[String].map(Phone(_))
    //implicit val codec: JsonCodec[Phone] = DeriveJsonCodec.gen[Phone]
  }
  object Phone extends SubtypeSmart[String](matchesRegex("""^(\+\d{1,2}\s)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}$""")){
    implicit val codec: JsonCodec[Phone] = DeriveJsonCodec.gen[Phone]
  }
  type Phone = Phone.type
  object Email extends SubtypeSmart[String](Assertion.assertion("isEmailValid")()(emailValidator.isValid(_))) {
    implicit val codec: JsonCodec[Email] = DeriveJsonCodec.gen[Email]
    //implicit val encoder: JsonEncoder[Validation[String, Email.Type]] = JsonEncoder[String].contramap(_.fold(e => e.mkString, s => s))
    //implicit val decoder: JsonDecoder[Validation[String, Email.Type]] = JsonDecoder[String].map(Email.make(_))
  }
  object Phone extends SubtypeSmart[String](matchesRegex("""^(\+\d{1,2}\s)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}$""")){
    implicit val encoder: JsonEncoder[Validation[String, Phone.Type]] = JsonEncoder[String].contramap(_.fold(e => e.mkString, Phone.unwrap(_)))
    implicit val decoder: JsonDecoder[Validation[String, Phone.Type]] = JsonDecoder[String].map(Phone.make(_))
    //implicit val codec: JsonCodec[Phone] = DeriveJsonCodec.gen[Phone]
  }
  type Phone = Phone.type

  object Email extends SubtypeSmart[String](Assertion.assertion("isEmailValid")()(emailValidator.isValid(_))) {
    //implicit val codec: JsonCodec[Email] = DeriveJsonCodec.gen[Email]
    implicit val encoder: JsonEncoder[Validation[String, Email.Type]] = JsonEncoder[String].contramap(_.fold(e => e.mkString, Email.unwrap(_)))
    implicit val decoder: JsonDecoder[Validation[String, Email.Type]] = JsonDecoder[String].map(Email.make(_))
    implicit val encoderOptSeq: JsonEncoder[Validation[String, Option[Seq[Email.Type]]]] = JsonEncoder[Option[Seq[String]]].contramap(_.fold(e => e.map(xs => Seq(xs.mkString)).headOption, s => s.map(xs => xs.map(x => Email.unwrap(x)))))
  }
  type Email = Email.type
   */
  case class Phone(value: String) extends AnyVal
  object Phone {
    implicit val codec: JsonCodec[Phone] = DeriveJsonCodec.gen[Phone]
  }
  case class Email(value: String) extends AnyVal
  object Email {
    implicit val codec: JsonCodec[Email] = DeriveJsonCodec.gen[Email]
  }

  case class Access (
                      manageGroupMembership: Boolean,
                      view: Boolean,
                      mapRoles: Boolean,
                      impersonate: Boolean,
                      manage: Boolean
                    )
  object Access {
    implicit val codec: JsonCodec[Access] = DeriveJsonCodec.gen[Access]
  }

  case class Attributes (
                          phone: Option[Seq[Phone]] = None,
                          phone2: Option[Seq[Phone]] = None,
                          phone3: Option[Seq[Phone]] = None,
                          pancard: Option[Seq[String]] = None,
                          passport: Option[Seq[String]] = None
                        )
  object Attributes {
    import Phone._
    import Email._
    implicit val codec: JsonCodec[Attributes] = DeriveJsonCodec.gen[Attributes]
  }

  case class KeycloakUser (
                             id: Option[String] = None,
                             createdTimestamp: Option[Long] = None,
                             username: String,
                             enabled: Boolean = true,
                             totp: Option[Boolean] = None,
                             emailVerified: Boolean = false,
                             firstName: String,
                             lastName: String,
                             email: Option[Email] = None,
                             attributes: Option[Attributes] = None,
                             requiredActions: Seq[String] = Seq.empty,
                             notBefore: Int = 0,
                             access: Option[Access] = None
                           )
  object KeycloakUser {
    import Phone._
    import Email._
    implicit val codec: JsonCodec[KeycloakUser] = DeriveJsonCodec.gen[KeycloakUser]
  }


  sealed trait ApiResponseMessage

  case class ApiResponseError(code: Option[String] = None, errorMessage: String) extends ApiResponseMessage
  object ApiResponseError {
    implicit val codec: JsonCodec[ApiResponseError] = DeriveJsonCodec.gen[ApiResponseError]
  }

  case class ApiResponseSuccess(code: Option[String] = None, message: String) extends ApiResponseMessage
  object ApiResponseSuccess{
    implicit val codec: JsonCodec[ApiResponseSuccess] = DeriveJsonCodec.gen[ApiResponseSuccess]
  }

  case class ServiceContext(token: String, requestId: Option[UUID] = Option(UUID.randomUUID()), readTimeout: FiniteDuration = 30.seconds, connectTimeout: FiniteDuration = 5.seconds)
  object ServiceContext {
    implicit val encoder: JsonEncoder[FiniteDuration] = JsonEncoder[Long].contramap(_._1)
    implicit val decoder: JsonDecoder[FiniteDuration] = JsonDecoder[Long].map(FiniteDuration(_, TimeUnit.SECONDS))
    //implicit val codec: JsonCodec[ServiceContext] = DeriveJsonCodec.gen[ServiceContext]
  }
}
