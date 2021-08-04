package com.bootes.dao.repository

import com.bootes.dao.User
import io.getquill.context.ZioJdbc.QuillZioExt
import zio._
import zio.blocking.Blocking

import java.io.Closeable
import javax.sql.DataSource

case class UserRepositoryLive(dataSource: DataSource with Closeable, blocking: Blocking.Service) extends UserRepository {
  val dataSourceLayer: Has[DataSource with Closeable] with Has[Blocking.Service] = Has.allOf[DataSource with Closeable, Blocking.Service](dataSource, blocking)

  import MyContext._

  override def create(user: User): Task[User] = transaction {
    for {
      id     <- run(UserQueries.insertUser(user).returning(_.id))
      users <- {
        run(UserQueries.byId(id))
      }
    } yield users.headOption.getOrElse(throw new Exception("Insert failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)

  override def all: Task[Seq[User]] = run(UserQueries.usersQuery).dependOnDataSource().provide(dataSourceLayer)

  override def findById(id: Long): Task[User] = {
    for {
      results <- run(UserQueries.byId(id)).dependOnDataSource().provide(dataSourceLayer)
      user    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find user with id $id", id.toString))
    } yield user
  }

  override def findByCode(code: String): Task[User] = {
    for {
      results <- run(UserQueries.byCode(code)).dependOnDataSource().provide(dataSourceLayer)
      user    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find user with code, $code", code))
    } yield user
  }

  override def findByEmail(email: String): Task[User] = {
    for {
      results <- run(UserQueries.byEmail(email)).dependOnDataSource().provide(dataSourceLayer)
      user    <- ZIO.fromOption(results.headOption).orElseFail(NotFoundException(s"Could not find user with email, $email", email))
    } yield user
  }

  override def filter(values: Seq[FieldValue]): Task[Seq[User]] = {
    for {
      results <- run(UserQueries.byEmail("")).dependOnDataSource().provide(dataSourceLayer)
      user    <- ZIO.effect(results).orElseFail(NotFoundException(s"Could not find users with input criteria, ${values.toString()}", values.mkString(", ")))
    } yield user
  }

  override def update(user: User): Task[User] = transaction {
    for {
      _     <- run(UserQueries.upsertUser(user))
      users <- run(UserQueries.byId(user.id))
    } yield users.headOption.getOrElse(throw new Exception("Update failed!"))
  }.dependOnDataSource().provide(dataSourceLayer)
}

object UserQueries {

  import MyContext._

  // NOTE - if you put the type here you get a 'dynamic query' - which will never wind up working...
  implicit val userSchemaMeta = schemaMeta[User](""""user"""")
  implicit val userInsertMeta = insertMeta[User](_.id)

  val usersQuery                   = quote(query[User])
  def byId(id: Long)               = quote(usersQuery.filter(_.id == lift(id)))
  def byCode(code: String)               = quote(usersQuery.filter(_.code == lift(code)))
  def byEmail(email: String)               = quote(usersQuery.filter(_.pii.email1 == lift(Some(email):Option[String])))
  def filter(values: Seq[FieldValue])               = quote(query[User])
  def insertUser(user: User) = quote(usersQuery.insert(lift(user)))
  def upsertUser(user: User) = quote(usersQuery.update(lift(user)))
}
