package com.bootes.dao.keycloak

import zio.console.Console
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.macros.accessible

import java.util.UUID

object Models {

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

  case class ServiceContext(token: String, requestId: Option[UUID] = Option(UUID.randomUUID()))
  object ServiceContext {
    implicit val codec: JsonCodec[ServiceContext] = DeriveJsonCodec.gen[ServiceContext]
  }
}
