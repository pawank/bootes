package com.bootes.dao

import com.bootes.client.{FormUrlEncoded, FormUsingJson, NoContent, ZSttpClient}
import com.bootes.config.Configuration.{KeycloakConfig, keycloakConfigDescription, keycloakConfigLayer, keycloakConfigValue}
import com.bootes.dao.keycloak.Models.{ApiResponseError, ApiResponseSuccess, Attributes, CredentialRepresentation, Email, KeycloakUser, KeycloakUsers, Phone, QueryParams, ServiceContext, UserAlreadyExists, UserDoesNotExists}
import com.bootes.dao.repository.{JSONB, UserRepository}
import com.bootes.server.UserServer
import com.bootes.server.UserServer.{CorrelationId, DebugJsonLog}
import com.bootes.server.auth.{ApiToken, LogoutRequest}
import com.bootes.server.auth.keycloak.KeycloakClientExample.{loginUrl, userCreateUrl, usersUrl}
import io.getquill.Embedded
import io.scalaland.chimney.dsl.TransformerOps
import sttp.client3.{Response, basicRequest}
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.clock.Clock
import zio.{Has, IO, RIO, RLayer, Task, ZIO, system}
import zio.console.Console
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.macros.accessible
import zio.console._
import zio.json._
import zio.logging.{LogAnnotation, Logging, log}
import zio.prelude.Validation

import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.language.implicitConversions

case class PrimaryAddress(houseNo: String, street: Option[String] = None, pincode: Option[String] = None,
                   landmark: Option[String] = None, city: Option[String] = None,
                   state: Option[String] = None,
                   country: String = "IN",
                   fullAddress: Option[String] = None) extends Embedded

object PrimaryAddress {
  implicit val codec: JsonCodec[PrimaryAddress] = DeriveJsonCodec.gen[PrimaryAddress]
}

case class Address(id: Long = -1, `type`: String, houseNo: String, street: Option[String] = None, pincode: Option[String] = None,
                   landmark: Option[String] = None, city: Option[String] = None,
                   state: Option[String] = None,
                   country: String = "IN",
                   fullAddress: Option[String] = None,
                   metadata: Option[Metadata] = Some(Metadata.default),
                   userId: Long)

object Address {
  implicit val codec: JsonCodec[Address] = DeriveJsonCodec.gen[Address]
}

case class Metadata(createdAt: java.time.ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC")),
                    updatedAt: Option[ZonedDateTime],
                    createdBy: String = "system",
                    updatedBy: Option[String]) extends Embedded
object Metadata {
  implicit val codec: JsonCodec[Metadata] = DeriveJsonCodec.gen[Metadata]
  val default = Metadata(createdAt = ZonedDateTime.now(ZoneId.of("UTC")), createdBy = "system", updatedAt = None, updatedBy = None)
}

case class VectorInfo(pancard: Option[String],
                      passportNo: Option[String]) extends Embedded
object VectorInfo {
  implicit val codec: JsonCodec[VectorInfo] = DeriveJsonCodec.gen[VectorInfo]
}


case class LegalEntity(registrationNo: String,
                       legalName: String,
                       `type`: String,
                       mailingAddress: Option[Address],
                       registeredAddress: Option[Address],
                       status: String) extends Embedded
object LegalEntity {
  implicit val codec: JsonCodec[LegalEntity] = DeriveJsonCodec.gen[LegalEntity]
}

case class ContactMethod(email1: Option[String] = None,
                         email2: Option[String] = None,
                         email3: Option[String] = None,
                         phone1: Option[String] = None,
                         phone2: Option[String] = None,
                         phone3: Option[String] = None) extends Embedded
object ContactMethod {
  implicit val codec: JsonCodec[ContactMethod] = DeriveJsonCodec.gen[ContactMethod]
}

case class Name(firstName: String,
                   middleName: Option[String] = None,
                   lastName: String,
                   ) extends Embedded
object Name {
  import Email._
  import Phone._
  implicit val codec: JsonCodec[Name] = DeriveJsonCodec.gen[Name]
}

case class Attribute(key: String, value: String)
object Attribute {
  implicit val codec: JsonCodec[Attribute] = DeriveJsonCodec.gen[Attribute]
}


case class User(
                 id: Option[UUID],
                 `type`: String,
                 code: String,
                 name: Name,
                 contactMethod: Option[ContactMethod] = None,
                 vector: Option[VectorInfo] = None,
                 gender: Option[String] = None,
                 dateOfBirth: Option[java.time.LocalDate] = None,
                 placeOfBirth: Option[String] = None,
                 primaryAddress: Option[PrimaryAddress] = None,
                 //legalEntity: LegalEntity,
                 roles: List[String] = List.empty,
                 scopes: List[String] = List.empty,
                 status: String = "active",
                 metadata: Option[Metadata] = None
  )

case class CreateUserRequest (
                 id: Option[UUID] = None,
                 requestId: Option[String] = None,
                 `type`: String,
                 code: String,
                 password: Option[String] = None,
                 name: Name,
                 contactMethod: Option[ContactMethod] = None,
                 vector: Option[VectorInfo] = None,
                 gender: Option[String] = None,
                 dateOfBirth: Option[java.time.LocalDate] = None,
                 placeOfBirth: Option[String] = None,
                 address: Option[Address] = None,
                 //attributes: JSONB = JSONB.apply(String.valueOf("{}").getBytes()),
                 roles: List[String] = List.empty,
                 scopes: List[String] = List.empty,
                 status: String = "active",
                   metadata: Option[Metadata] = None
               )
object CreateUserRequest {
  implicit val codec: JsonCodec[CreateUserRequest] = DeriveJsonCodec.gen[CreateUserRequest]
  val sample = CreateUserRequest(`type` = "real", code = "111", name = Name("X", None, "Z"), status = "active")

  implicit def toKeycloakUser(user: CreateUserRequest): KeycloakUser = KeycloakUser(id = user.id.map(_.toString), username = user.code, firstName = user.name.firstName, lastName = user.name.lastName, email = user.contactMethod.map(_.email1).flatten,
    attributes = user.vector.map(v => Attributes(pancard = v.pancard.map(Seq(_)), passport = v.passportNo.map(Seq(_)))),
    credentials = user.password.map(p => CredentialRepresentation(temporary = false, `type` = "password", value = p)).toSet
  )
}

case class ResponseMessage(status: Boolean, code: Int, message: String, details: Option[String] = None)
object ResponseMessage {
  implicit val codec: JsonCodec[ResponseMessage] = DeriveJsonCodec.gen[ResponseMessage]
  def makeSuccess(code: Int, message: String) = ResponseMessage(status = true, code = code, message = message, details = None)
}


object User {
  implicit def fromUserRecord(record: CreateUserRequest): User               = record.into[User].transform.copy(metadata = Some(Metadata.default))
  implicit def fromSeqUserRecord(records: Seq[CreateUserRequest]): Seq[User] = records.map(fromUserRecord)
  implicit val codec: JsonCodec[User]                                 = DeriveJsonCodec.gen[User]

  val sample = User(id = None, `type` = "real", code = "1", name = Name(firstName = "Pawan", lastName = "Kumar"), status = "active", metadata = Some(Metadata.default))

  implicit def fromKeycloakUser(keycloakUser: KeycloakUser): User = {
    val phone1xs: Seq[String] = keycloakUser.attributes.map(_.phone.getOrElse(Seq.empty)).getOrElse(Seq.empty)
    val phone1 = if (phone1xs.isEmpty) None else phone1xs.headOption
    User(id = keycloakUser.id.map(UUID.fromString(_)), `type` = "real", code = keycloakUser.username, name = Name(firstName = keycloakUser.firstName, lastName = keycloakUser.lastName), contactMethod = Some(ContactMethod(email1 = keycloakUser.email, phone1 = phone1)),
      status = if (keycloakUser.enabled) "active" else "inactive",
      metadata = Some(Metadata.default)
    )
  }

  implicit def fromKeycloakCreateUserRequest(keycloakUser: KeycloakUser): CreateUserRequest = {
    val phone1xs: Seq[String] = keycloakUser.attributes.map(_.phone.getOrElse(Seq.empty)).getOrElse(Seq.empty)
    val phone1 = if (phone1xs.isEmpty) None else phone1xs.headOption
    CreateUserRequest(id = keycloakUser.id.map(UUID.fromString(_)), `type` = "real", code = keycloakUser.username, name = Name(firstName = keycloakUser.firstName, lastName = keycloakUser.lastName), contactMethod = Some(ContactMethod(email1 = keycloakUser.email, phone1 = phone1)),
      status = if (keycloakUser.enabled) "active" else "inactive", metadata = Some(Metadata.default)
    )
  }
}

@accessible
trait UserService {
  def create(request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User]
  def upsert(request: CreateUserRequest, methodType:Option[String])(implicit ctx: ServiceContext): ZIO[Any, Serializable, User]
  def update(id: UUID, request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User]
  def all(params: Option[QueryParams])(implicit ctx: ServiceContext): Task[Seq[User]]
  def get(id: UUID)(implicit ctx: ServiceContext): Task[User]
  def get(code: String)(implicit ctx: ServiceContext): Task[User]
  def getByEmail(email: String)(implicit ctx: ServiceContext): Task[User]
  def logout(id: String, inputRequest: LogoutRequest)(implicit ctx: ServiceContext): Task[ResponseMessage]
}

object UserService {
  val layer: RLayer[Has[UserRepository] with Console, Has[UserService]] = (UserServiceLive(_, _)).toLayer
  val layerKeycloakService: RLayer[SttpClient with Console, Has[UserService]] = (KeycloakUserServiceLive(_)).toLayer
}

case class UserServiceLive(repository: UserRepository, console: Console.Service) extends UserService {

  override def upsert(request: CreateUserRequest, methodType:Option[String] = Some("post"))(implicit ctx: ServiceContext): ZIO[Any, Serializable, User] = {
    repository.upsert(User.fromUserRecord(request))
  }
  override def create(request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User] = {
    repository.create(User.fromUserRecord(request))
  }

  override def all(params: Option[QueryParams])(implicit ctx: ServiceContext): Task[Seq[User]] = for {
    users <- repository.all
    _     <- console.putStrLn(s"Users: ${users.map(_.code).mkString(",")}")
  } yield users.sortBy(_.id)

  override def get(id: UUID)(implicit ctx: ServiceContext): Task[User] = repository.findById(id)

  override def update(id: UUID, request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User] =  {
    repository.update(User.fromUserRecord(request).copy(id = Some(id)))
  }

  override def get(code: String)(implicit ctx: ServiceContext): Task[User] = repository.findByCode(code)

  override def getByEmail(email: String)(implicit ctx: ServiceContext): Task[User] = repository.findByEmail(email)

  def logout(id: String, inputRequest: LogoutRequest)(implicit ctx: ServiceContext): Task[ResponseMessage] = Task.succeed(ResponseMessage.makeSuccess(200, ""))
}
case class KeycloakUserServiceLive(console: Console.Service) extends UserService {
  import sttp.client3._
  import sttp.client3.asynchttpclient.zio._

  override def create(request: CreateUserRequest)(implicit serviceContext: ServiceContext): Task[User] = {
    ???
  }

  override def upsert(request: CreateUserRequest, methodType:Option[String] = Some("post"))(implicit serviceContext: ServiceContext): ZIO[Any, Serializable, User] = {
    val inputRequest = CreateUserRequest.toKeycloakUser(request)
      val result = for {
      configValue <- keycloakConfigValue
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(configValue.toString)))(
                                                                                                log.debug(s"Loaded config")
                                                                                                )
      url = s"${configValue.keycloak.url}/${configValue.keycloak.adminUsername}/realms/${configValue.keycloak.realm.getOrElse("")}/users"
      res <- {
        methodType match {
          case Some(m)  if m.equalsIgnoreCase("put") =>
            val url = s"${configValue.keycloak.url}/${configValue.keycloak.adminUsername}/realms/${configValue.keycloak.realm.getOrElse("")}/users/${request.id.getOrElse(UUID.randomUUID.toString).toString}"
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(url)))(
              log.debug(s"Upsert call to $url with method = $methodType")
            ) &>
            ZSttpClient.postOrPut("put", url, inputRequest, classOf[ApiResponseSuccess], FormUsingJson)
          case _ =>
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(url)))(
              log.debug(s"Upsert call to $url with method = $methodType")
            ) &>
            ZSttpClient.postOrPut("post", url, inputRequest, classOf[ApiResponseSuccess], FormUsingJson)
        }
      }
      output <- {
        res match {
          case Right(data) =>
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(data.toString)))(
              log.debug(s"Got response for $url")
            ) &>
            ZIO.succeed(User.fromUserRecord(request).copy(id = None))
          case Left(data) =>
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(data)))(
              log.debug(s"Error received for $url with response: $data")
            ) &> {
              val error: String = data.fromJson[ApiResponseError].fold(s => s, c => c.errorMessage)
              log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(data)))(
                log.debug(s"Error for $url")
              ) &> ZIO.fail(error)
            }
        }
      }
    } yield output
    result
      .mapError(someError =>
              someError match {
                case Right(v) =>
                    //new RuntimeException(s"Success: $v")
                  UserAlreadyExists(v.toString)
                  case Left(e) =>
                    //new RuntimeException(s"Error: $e")
                    UserAlreadyExists(e.toString)
                }
        )
      .provideLayer(Clock.live ++ UserServer.logLayer ++ system.System.live)
        //result.mapError(e => Left(e)).provideLayer(Clock.live ++ UserServer.logLayer ++ system.System.live)
  }

  override def all(params: Option[QueryParams])(implicit serviceContext: ServiceContext): Task[Seq[User]] = {
    val result = for {
      configValue <- keycloakConfigValue
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(configValue.toString)))(
        log.debug(s"Loaded config for getting all users")
      )
      //url = s"${configValue.keycloak.url}/${configValue.keycloak.adminUsername}/realms/${configValue.keycloak.realm.getOrElse("")}/users"
      url = s"${configValue.keycloak.url}/realms/${configValue.keycloak.masterRealm}/extended-api/realms/${configValue.keycloak.realm.getOrElse("")}/users"
      res <- {
        //ZSttpClient.getCollection(url, CreateUserRequest.sample, classOf[List[KeycloakUser]], FormUsingJson)
        ZSttpClient.get(url, CreateUserRequest.sample, classOf[KeycloakUsers], FormUsingJson, params)
      }
      output <- {
        res match {
          case Right(data) =>
            ZIO.succeed(data.users.map(x => User.fromKeycloakUser(x)))
            //ZIO.succeed(data.map(x => User.fromKeycloakUser(x)))
          case Left(data) =>
            val error: String = data.fromJson[ApiResponseError].fold(s => s, c => c.errorMessage)
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(data)))(
              log.debug(s"Error, $error for $url")
            ) &>
              ZIO.succeed(List.empty)
        }
      }
    } yield output
    result
      .mapError(error => new RuntimeException(s"Error: $error") )
      .provideLayer(AsyncHttpClientZioBackend.layer() ++ Clock.live ++ UserServer.logLayer ++ system.System.live)
  }

  override def get(id: UUID)(implicit serviceContext: ServiceContext): Task[User] = {
    val result = for {
      configValue <- keycloakConfigValue
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(configValue.toString)))(
        log.debug(s"Loaded config for getting user by id, $id")
      )
      url = s"${configValue.keycloak.url}/${configValue.keycloak.adminUsername}/realms/${configValue.keycloak.realm.getOrElse("")}/users/${id.toString}"
      res <- {
        ZSttpClient.get(url, CreateUserRequest.sample, classOf[KeycloakUser], FormUsingJson)
      }
      output <- {
        res match {
          case Right(data) =>
            ZIO.succeed(User.fromKeycloakUser(data))
          case Left(data) =>
            val error: String = data.fromJson[ApiResponseError].fold(s => s, c => c.errorMessage)
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(data)))(
              log.debug(s"Error, $error for $url")
            ) &>
            ZIO.fail(error)
        }
      }
    } yield output
    result
      .mapError(error => new RuntimeException(s"Error: $error") )
      .provideLayer(AsyncHttpClientZioBackend.layer() ++ Clock.live ++ UserServer.logLayer ++ system.System.live)
  }

  override def update(id: UUID, request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User] =  {
    upsert(request.copy(id = Some(id)), Some("put")).mapError(e => UserDoesNotExists(e.toString))
  }

  override def get(code: String)(implicit serviceContext: ServiceContext): Task[User] = {

    val result = for {
      configValue <- keycloakConfigValue
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(configValue.toString)))(
        log.debug(s"Loaded config for getting user by id, $code")
      )
      url = s"${configValue.keycloak.url}/realms/${configValue.keycloak.masterRealm}/extended-api/realms/${configValue.keycloak.realm.getOrElse("")}/users"
      res <- {
        ZSttpClient.get(url, CreateUserRequest.sample, classOf[KeycloakUsers], FormUsingJson)
      }
      output <- {
        res match {
          case Right(data) =>
            ZIO.succeed(User.sample)
          //ZIO.succeed(data.users.map(x => User.fromKeycloakUser(x)))
          case Left(data) =>
            val error: String = data.fromJson[ApiResponseError].fold(s => s, c => c.errorMessage)
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(data)))(
              log.debug(s"Error, $error for $url")
            ) &>
              ZIO.fail(error)
        }
      }
    } yield output
    result
      .mapError(error => new RuntimeException(s"Error: $error") )
      .provideLayer(AsyncHttpClientZioBackend.layer() ++ Clock.live ++ UserServer.logLayer ++ system.System.live)
  }

  override def getByEmail(email: String)(implicit ctx: ServiceContext): Task[User] =
    ZIO.effect(User.sample)

  override def logout(id: String, inputRequest: LogoutRequest)(implicit serviceContext: ServiceContext): Task[ResponseMessage] = {
    val result = for {
      configValue <- keycloakConfigValue
      _ <- log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(configValue.toString)))(
        log.debug(s"Loaded config")
      )
      url = s"${configValue.keycloak.url}/realms/${configValue.keycloak.realm.getOrElse("")}/protocol/openid-connect/logout"
      res <- {
        ZSttpClient.postOrPut("post", url, inputRequest, classOf[NoContent], FormUrlEncoded)
      }
      output <- {
        res match {
          case Right(data) =>
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(data.toString)))(
              log.debug(s"Got response for $url")
            ) &>
              ZIO.succeed(ResponseMessage(status = true, code = 200, message = "Logout done"))
          case Left(data) =>
            val error: String = data.fromJson[ApiResponseError].fold(s => s, c => c.errorMessage)
            log.locally(CorrelationId(serviceContext.requestId).andThen(DebugJsonLog(data)))(
              log.debug(s"Error for $url")
            ) &>
              ZIO.fail(error)
        }
      }
    } yield output
    result
      .mapError(someError =>
        someError match {
          case Right(v) =>
            new RuntimeException(s"Success: $v")
          case Left(e) =>
            new RuntimeException(s"Error: $e")
        }
      )
      .provideLayer(Clock.live ++ UserServer.logLayer ++ system.System.live)
  }
}
