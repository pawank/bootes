package com.bootes.utils

import scala.io.Source
import zio.{App, ExitCode}
import com.typesafe.config.Config
import zio.config._
import zio.config.syntax._
import zio.config.magnolia._

import zemail.email
import zemail.email.MailerOps

import courier.{Mailer, _}

object EmailUtils {
  val welcomeTemplate: String = Source.fromFile("src/main/resources/welcome_email.html").mkString
  val formSubmissionTemplate: String = Source.fromFile("src/main/resources/form_submission.html").mkString
  val formElementTemplate: String = Source.fromFile("src/main/resources/form_section.html").mkString
  
  val builder = 
    Mailer(System.getenv("EMAIL_SERVER_HOSTNAME"), System.getenv("EMAIL_SERVER_PORT").toInt)
    .auth(true)
    .as(System.getenv("EMAIL_SERVER_USERNAME"), System.getenv("EMAIL_SERVER_PASSWORD"))
    .startTls(true)()

  def send(from: String, to: String, subject: String, body: String) = {
   val envelop = Envelope
    .from(javax.mail.internet.InternetAddress.parse(from).head)
    .to(javax.mail.internet.InternetAddress.parse(to).head)
    .subject(subject)
    .content(Multipart().html(body))
    val client = for {
      
      _ <- email.send(envelop)
    } yield ()
    client
  }

}
