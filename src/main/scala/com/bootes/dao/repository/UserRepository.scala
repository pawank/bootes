package com.bootes.dao.repository

import com.bootes.dao.{CreateUserRequest, User}
import io.getquill.context.ZioJdbc.QDataSource
import zio._
import zio.macros.accessible

import java.util.UUID


case class FieldValue(field: String, value: String)

@accessible
trait UserRepository {
  def create(user: User): Task[User]
  def update(user: User): Task[User]
  def upsert(user: User, methodType: Option[String] = Some("post")): Task[User]
  def all: Task[Seq[User]]
  def filter(values: Seq[FieldValue]): Task[Seq[User]]
  def findById(id: UUID): Task[User]
  def findByCode(code: String): Task[User]
  def findByEmail(email: String): Task[User]
}

object UserRepository {
  val layer: URLayer[QDataSource, Has[UserRepository]] = (UserRepositoryLive(_, _)).toLayer
}
