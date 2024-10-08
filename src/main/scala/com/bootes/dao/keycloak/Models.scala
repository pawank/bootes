package com.bootes.dao.keycloak

import sttp.client3.UriContext
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
    implicit def asPhone(value: String) = Phone(value)
  }
  case class Email(value: String) extends AnyVal
  object Email {
    implicit val codec: JsonCodec[Email] = DeriveJsonCodec.gen[Email]
    implicit def asEmail(value: String) = Email(value)
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
                          phone: Option[Seq[String]] = None,
                          phone2: Option[Seq[String]] = None,
                          phone3: Option[Seq[String]] = None,
                          pancard: Option[Seq[String]] = None,
                          passport: Option[Seq[String]] = None
                        )
  object Attributes {
    implicit val codec: JsonCodec[Attributes] = DeriveJsonCodec.gen[Attributes]
  }

  case class CredentialRepresentation(temporary: Boolean, `type`: String, value: String)
  object CredentialRepresentation {
    implicit val codec: JsonCodec[CredentialRepresentation] = DeriveJsonCodec.gen[CredentialRepresentation]
  }

  case class KeycloakUser (
                             id: Option[String] = None,
                             createdTimestamp: Option[Long] = None,
                             username: String,
                             password: Option[String] = None,
                             enabled: Boolean = true,
                             totp: Option[Boolean] = None,
                             emailVerified: Boolean = false,
                             firstName: String,
                             lastName: String,
                             email: Option[String] = None,
                             attributes: Option[Attributes] = None,
                             requiredActions: Seq[String] = Seq.empty,
                             notBefore: Int = 0,
                             credentials: Set[CredentialRepresentation] = Set.empty,
                             access: Option[Access] = None
                           ) {
  }
  object KeycloakUser {
    implicit val codec: JsonCodec[KeycloakUser] = DeriveJsonCodec.gen[KeycloakUser]
  }

  case class KeycloakUsers(users: Seq[KeycloakUser], count: Option[Int] = None)
  object KeycloakUsers {
    implicit val codec: JsonCodec[KeycloakUsers] = DeriveJsonCodec.gen[KeycloakUsers]
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

  case class KeyValue(key: String, value: String)
  object KeyValue{
    implicit val codec: JsonCodec[KeyValue] = DeriveJsonCodec.gen[KeyValue]
  }

  case class QueryParams(params: Set[KeyValue]) {
    val isFound = !params.isEmpty
  }
  object QueryParams{
    implicit val codec: JsonCodec[QueryParams] = DeriveJsonCodec.gen[QueryParams]
    def makeQueryString(params: Option[QueryParams], withQuestionMark: Boolean = true) = {
      val qs = params match {
        case Some(kv) => kv.params.map(x => s"${x.key}=${x.value}").mkString("&")
        case _ => ""
      }
      if (withQuestionMark) s"?$qs" else qs
    }
    def makeUri(params: Option[QueryParams], withQuestionMark: Boolean = true) = {
      val qs = params match {
        case Some(kv) => kv.params.map(x => uri"${x.key}=${x.value}").mkString("&")
        case _ => ""
      }
      if (withQuestionMark) s"?$qs" else qs
    }
  }

  case class ServiceContext(token: String, requestId: UUID, readTimeout: FiniteDuration = 30.seconds, connectTimeout: FiniteDuration = 5.seconds) {
    override def toString: String = s"Token: ****** for requestId: $requestId"
  }
  object ServiceContext {
    implicit val encoder: JsonEncoder[FiniteDuration] = JsonEncoder[Long].contramap(_._1)
    implicit val decoder: JsonDecoder[FiniteDuration] = JsonDecoder[Long].map(FiniteDuration(_, TimeUnit.SECONDS))
    //implicit val codec: JsonCodec[ServiceContext] = DeriveJsonCodec.gen[ServiceContext]
    def newRequestId() = UUID.randomUUID()
  }


  case class UserDoesNotExists(message: String) extends Exception(message)
  case class UserAlreadyExists(message: String) extends Exception(message)
  case class RequestParsingError(message: String) extends Exception(message)
}
