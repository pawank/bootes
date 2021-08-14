package com.bootes.dao.keycloak

import com.bootes.dao.keycloak.Models.Phone
import zio.console.Console
import zio.json.{DeriveJsonCodec, JsonCodec, JsonDecoder, JsonEncoder}
import zio.macros.accessible
import zio.prelude.{SubtypeSmart, Validation}
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
   */
  object Phone extends SubtypeSmart[String](matchesRegex("""^(\+\d{1,2}\s)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}$""")){
    implicit val codec: JsonCodec[Phone] = DeriveJsonCodec.gen[Phone]
  }
  type Phone = Phone.type

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
                             email: Option[String] = None,
                             attributes: Option[Attributes] = None,
                             requiredActions: Seq[String] = Seq.empty,
                             notBefore: Int = 0,
                             access: Option[Access] = None
                           )
  object KeycloakUser {
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
