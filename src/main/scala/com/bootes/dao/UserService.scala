package com.bootes.dao

import com.bootes.dao.keycloak.Models.{KeycloakError, KeycloakSuccess, KeycloakUser, ServiceContext}
import com.bootes.dao.repository.{JSONB, UserRepository}
import com.bootes.server.auth.ApiToken
import com.bootes.server.auth.keycloak.KeycloakClientExample.{loginUrl, userCreateUrl, usersUrl}
import io.getquill.Embedded
import io.scalaland.chimney.dsl.TransformerOps
import sttp.client3.{Response, basicRequest}
import sttp.client3.asynchttpclient.zio.SttpClient
import zio.{Has, RIO, RLayer, Task, ZIO}
import zio.console.Console
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.macros.accessible
import zio.console._
import zio.json._
import zio.{Has, IO, ZIO}

import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}
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
  val default = Metadata(updatedAt = None, updatedBy = None)
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


case class PiiInfo(firstName: String,
                   middleName: Option[String] = None,
                   lastName: String,
                   email1: Option[String] = None,
                   email2: Option[String] = None,
                   email3: Option[String] = None,
                   phone1: Option[String] = None,
                   phone2: Option[String] = None,
                   phone3: Option[String] = None) extends Embedded
object PiiInfo {
  implicit val codec: JsonCodec[PiiInfo] = DeriveJsonCodec.gen[PiiInfo]
}

case class Attribute(key: String, value: String)
object Attribute {
  implicit val codec: JsonCodec[Attribute] = DeriveJsonCodec.gen[Attribute]
}


case class User(
  id: Long = -1,
  `type`: String,
  code: String,
  pii: PiiInfo,
  vector: Option[VectorInfo] = None,
  gender: Option[String] = None,
  dateOfBirth: Option[java.time.LocalDate] = None,
  placeOfBirth: Option[String] = None,
  address: Option[PrimaryAddress] = None,
  //legalEntity: LegalEntity,
  roles: List[String] = List.empty,
  scopes: List[String] = List.empty,
  status: String = "active",
  metadata: Option[Metadata] = None
  )

case class CreateUserRequest (
                 `type`: String,
                 code: String,
                 pii: PiiInfo,
                 vector: Option[VectorInfo] = None,
                 gender: Option[String] = None,
                 dateOfBirth: Option[java.time.LocalDate] = None,
                 placeOfBirth: Option[String] = None,
                 address: Option[Address] = None,
                 //legalEntity: LegalEntity,
                 //attributes: Map[String, String] = Map.empty,
                 //attributes: JSONB = JSONB.apply(String.valueOf("{}").getBytes()),
                 roles: List[String] = List.empty,
                 scopes: List[String] = List.empty,
                 status: String = "active"
               )
object CreateUserRequest {
  implicit val codec: JsonCodec[CreateUserRequest] = DeriveJsonCodec.gen[CreateUserRequest]
  val sample = CreateUserRequest(`type` = "real", code = "111", pii = PiiInfo(firstName = "Pawan", lastName = "Kumar"), status = "active")
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

  val sample = User(`type` = "real", code = "1", pii = PiiInfo(firstName = "Pawan", lastName = "Kumar"), status = "active", metadata = Some(Metadata.default))

  implicit def fromKeycloakUser(keycloakUser: KeycloakUser): User = {
    User(id = -1, `type` = "real", code = keycloakUser.username, pii = PiiInfo(firstName = keycloakUser.firstName, lastName = keycloakUser.lastName, email1 = keycloakUser.email),
      status = if (keycloakUser.enabled) "active" else "inactive", metadata = Some(Metadata.default)
    )
  }
}

@accessible
trait UserService {
  def create(request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User]
  def update(id: Long, request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User]
  def all()(implicit ctx: ServiceContext): Task[Seq[User]]
  def get(id: Long)(implicit ctx: ServiceContext): Task[User]
  def get(code: String)(implicit ctx: ServiceContext): Task[User]
  def getByEmail(email: String)(implicit ctx: ServiceContext): Task[User]
}

object UserService {
  val layer: RLayer[Has[UserRepository] with Console, Has[UserService]] = (UserServiceLive(_, _)).toLayer
  val layerKeycloakService: RLayer[SttpClient with Console, Has[UserService]] = (KeycloakUserServiceLive(_)).toLayer
}

case class UserServiceLive(repository: UserRepository, console: Console.Service) extends UserService {

  override def create(request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User] = {
    repository.create(User.fromUserRecord(request))
  }

  override def all()(implicit ctx: ServiceContext): Task[Seq[User]] = for {
    users <- repository.all
    _     <- console.putStrLn(s"Users: ${users.map(_.code).mkString(",")}")
  } yield users.sortBy(_.id)

  override def get(id: Long)(implicit ctx: ServiceContext): Task[User] = repository.findById(id)

  override def update(id: Long, request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User] =  {
    repository.update(User.fromUserRecord(request).copy(id = id))
  }

  override def get(code: String)(implicit ctx: ServiceContext): Task[User] = repository.findByCode(code)

  override def getByEmail(email: String)(implicit ctx: ServiceContext): Task[User] = repository.findByEmail(email)
}
case class KeycloakUserServiceLive(console: Console.Service) extends UserService {
  import sttp.client3._
  import sttp.client3.asynchttpclient.zio._
  val loginUrl: String                                   = "http://localhost:8180/auth/realms/master/protocol/openid-connect/token"
  val usersUrl: String                                   = "http://localhost:8180/auth/admin/realms/rapidor/users"

  override def create(request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User] = {
    val token = ctx.token
    val payload = KeycloakUser(username = "pawan3", firstName = "pawan", lastName = "kumar", email = Some("pawan3@test.com")).toJson
    val req = basicRequest.contentType("application/json").auth.bearer(token).body(payload).post(uri"$userCreateUrl")
    println(s"Sending sttp create user request = $req")
    val response: ZIO[SttpClient, Throwable, Response[Either[String, String]]] = send(req)
    println(s"Response based on sttp create user = $response")
    //val result: ZIO[SttpClient, Throwable, Either[String, KeycloakSuccess]] = for {
    val result: ZIO[SttpClient, Serializable, Right[Nothing, KeycloakSuccess]] = for {
      res <- response
      output <- {
        Task{res}.flatMap(r => {
          println(s"Status code = ${r.code}")
          (r.code.code == 200) || (r.code.code == 201) match {
            case true =>
              r.body match {
                case Right(data) =>
                  println(s"Sttp zio response for user create = ${data}")
                  ZIO.succeed(Right(KeycloakSuccess(message = "Created")))
                case Left(error) =>
                  ZIO.fail(Left(s"<you shouldn't see this> $error"))
              }
            case _ =>
              r.body match {
                case Right(data) =>
                  val success: String = data.fromJson[KeycloakError].fold(s => s, c => c.errorMessage)
                  ZIO.fail(success)
                case Left(data) =>
                  println(s"Sttp zio response for user create = ${data}")
                  val error: String = data.fromJson[KeycloakError].fold(s => s, c => c.errorMessage)
                  ZIO.fail(Left(s"${error}"))
              }
          }
        })
      }
    } yield output
    //ZIO.effect(User.sample)
    result.map(r => User.fromUserRecord(request)).mapError(someError => {
      someError match {
        case Right(v) =>
          new RuntimeException(s"Success: $v")
        case Left(e) =>
          new RuntimeException(s"Error: $e")
      }
    }).provideLayer(AsyncHttpClientZioBackend.layer())
  }

  override def all()(implicit ctx: ServiceContext): Task[Seq[User]] = {
    val token = ctx.token
    val req = basicRequest.auth.bearer(token).get(uri"$usersUrl")
    println(s"Sending sttp create user request = $req")
    val response: ZIO[SttpClient, Throwable, Response[Either[String, String]]] = send(req)
    println(s"Response based on sttp create user = $response")
    //val result: ZIO[SttpClient, Throwable, Either[String, KeycloakSuccess]] = for {
    val result: ZIO[SttpClient, Serializable, Either[String, List[User]]] = for {
      res <- response
      output <- {
        Task{res}.flatMap(r => {
          println(s"Status code = ${r.code}")
          (r.code.code == 200) || (r.code.code == 201) match {
            case true =>
              r.body match {
                case Right(data) =>
                  println(s"Sttp zio response for user create = ${data}")
                  ZIO.succeed(data.fromJson[List[KeycloakUser]].map(xs => xs.map(x => User.fromKeycloakUser(x))))
                case Left(error) =>
                  ZIO.fail(Left(s"<you shouldn't see this> $error"))
              }
            case _ =>
              r.body match {
                case Right(data) =>
                  val success: String = data.fromJson[KeycloakError].fold(s => s, c => c.errorMessage)
                  ZIO.fail(success)
                case Left(data) =>
                  println(s"Sttp zio response for user create = ${data}")
                  val error: String = data.fromJson[KeycloakError].fold(s => s, c => c.errorMessage)
                  ZIO.fail(Left(s"${error}"))
              }
          }
        })
      }
    } yield output
    //ZIO.effect(User.sample)
    result.mapError(someError => {
      someError match {
        case Right(v) =>
          new RuntimeException(s"Success: $v")
        case Left(e) =>
          new RuntimeException(s"Error: $e")
      }
    }).provideLayer(AsyncHttpClientZioBackend.layer()).map(v => if (v.isLeft) Seq.empty else v.toSeq.flatten)
  }

  override def get(id: Long)(implicit ctx: ServiceContext): Task[User] = ZIO.effect(User.sample)

  override def update(id: Long, request: CreateUserRequest)(implicit ctx: ServiceContext): Task[User] =  {
    ZIO.effect(User.sample)
  }

  override def get(code: String)(implicit ctx: ServiceContext): Task[User] =
    ZIO.effect(User.sample)

  override def getByEmail(email: String)(implicit ctx: ServiceContext): Task[User] =
    ZIO.effect(User.sample)
}
