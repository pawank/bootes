package com.bootes.validators

import com.bootes.dao.keycloak.Models.{Email, Phone}
import com.bootes.server.auth.Token
import org.apache.commons.validator.routines.EmailValidator
import zio.prelude.Validation
import zio.test.Assertion
import zio.test.Assertion._

object Validators {
  val emailValidator:EmailValidator = EmailValidator.getInstance()

  /*
  Examples satisfying phone nos
  123-456-7890
(123) 456-7890
123 456 7890
123.456.7890
+91 (123) 456-7890
   */
  def validatePhone(value: String):Validation[String, Phone] = matchesRegex("""^(\+\d{1,2}\s)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}$""").test(value) match {
    case true => Validation.succeed(Phone(value))
    case false => Validation.fail(s"Phone value seems to be invalid, $value")
  }

  def validateEmail(value: String):Validation[String, Email] = emailValidator.isValid(value) match {
    case true => Validation.succeed(Email(value))
    case false => Validation.fail(s"Email value seems to be invalid, $value")
  }

  def validateName(value: String):Validation[String, String] = isNonEmptyString.test(value) && (matchesRegex("""^[-'a-zA-ZÀ-ÖØ-öø-ſ]+$""").test(value)) match {
    case true => Validation.succeed(value)
    case false => Validation.fail(s"Name value seems to be invalid, $value")
  }

  def validateToken(value: String):Validation[String, Token] = Validation.fromPredicateWith(s"Token value seems to be invalid, $value")(Token(value))(token => isNonEmptyString.test(value) && (isGreaterThan(1000) && isLessThan(2000)).test(value.size))
}
