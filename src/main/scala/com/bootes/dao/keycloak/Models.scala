package com.bootes.dao.keycloak

import zio.console.Console
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.macros.accessible

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
                          phone: Seq[String]
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

  case class KeycloakError(errorMessage: String)
  object KeycloakError {
    implicit val codec: JsonCodec[KeycloakError] = DeriveJsonCodec.gen[KeycloakError]
  }

}
