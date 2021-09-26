package com.bootes.validators

import com.bootes.dao.keycloak.Models.{Email, Phone}
import com.bootes.server.auth.Token
import com.data2ui.FormService
import com.data2ui.models.Models.{CreateElementRequest, Element, FormSection, Validations}
import org.apache.commons.validator.routines.EmailValidator
import zio.NonEmptyChunk
import zio.prelude.{Validation, ZValidation}
import zio.test.Assertion
import zio.test.Assertion._

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

object Validators {
  val emailValidator:EmailValidator = EmailValidator.getInstance()

  val dateFormatYYYYMMDD = "dd/MM/yyyy"
  val dateFormatddMMyyyy = "dd-MM-yyyy"
  val dateFormatyyyyMMdd2 = "yyyy-MM-dd"
  val dateFormatMMddyyyy = "MM-dd-yyyy"
  val dateFormatMMddyyyy2 = "MM/dd/yyyy"
  val dateWithFormatDDMMYYYYHHMM = "dd/MM/yyyy HH:mm:ss"
  val dateFormatUTC = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z"


  val availableDateFormats = Seq(dateFormatYYYYMMDD, dateFormatddMMyyyy, dateFormatyyyyMMdd2, dateFormatMMddyyyy, dateFormatMMddyyyy2)
  val availableDatetimeFormats = Seq(dateFormatUTC)
  def isValidDate(date: String): Boolean = {
      !availableDateFormats.map(format => {
        Try {
          val dtFormat = DateTimeFormatter.ofPattern(format)
          LocalDate.parse(date, dtFormat)
        } match {
          case Success(data) => true
          case Failure(ex) => false
        }
      }).filter(v => v == true).isEmpty
  }

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

  def validateTitle(value: String):Validation[String, String] = Validation.fromPredicateWith(s"Title cannot be empty, $value")(value)(token => isNonEmptyString.test(value) && (isGreaterThan(2) && isLessThan(200)).test(value.size))

  def validateValidationsElement(values: Seq[String], value: Validations):Validation[String, Validations] = {

    def check[T](values: Seq[String], predicate: String => Boolean): Boolean = {
      val (newVals: Seq[Boolean], vals: Seq[String], originalSize: Int) =  (values.map(x => predicate(x)), values, values.size)
      println(s"newVals = $newVals, vals = $vals and size = $originalSize")
      values.nonEmpty && (newVals.size == originalSize)
    }

    value.`type` match {
      case "int" | "number" | "integer" | "Int" | "Integer" =>
        def validateInt(x: String): Boolean = x.toInt.isValidInt
        val result = check(values, validateInt)
        if (result) Validation.succeed(value) else Validation.fail(s"`type`, number mismatch for the values provided")
      case "string" | "String" =>
        val result = !values.map(!_.isEmpty).isEmpty
        if (result) Validation.succeed(value) else Validation.fail(s"`type` mismatch for the values provided")
      case "bool" | "boolean" | "Boolean" =>
        val result = check(values, _.toBoolean)
        if (result) Validation.succeed(value) else Validation.fail(s"`type`, boolean mismatch for the values provided")
      case "date" | "datetime" | "Date" | "Dateime" =>
        val result = check(values, isValidDate)
        if (result) Validation.succeed(value) else Validation.fail(s"`type`, date mismatch for the values provided")
      case "range" | "Range" =>
        def validateInt(x: String): Boolean = x.toInt >= value.minimum.getOrElse(0) && x.toInt <= value.minimum.getOrElse(99999999)
        val result = check(values, validateInt)
        if (result) Validation.succeed(value) else Validation.fail(s"`type`, date mismatch for the values provided")
      case _ =>
        Validation.succeed(value)
    }
  }
  def validateFormSection(value: FormSection):Validation[FormSection, FormSection] = {
      var allErrors: scala.collection.mutable.Buffer[String] = scala.collection.mutable.Buffer.empty
      val elements: Seq[CreateElementRequest] = value.elements.map(e => {
        val title = validateTitle(e.name).toEither
        val sectionName = validateTitle(e.sectionName.getOrElse("")).toEither
        val chkType = validateTitle(e.`type`).toEither
        val result: Seq[Either[NonEmptyChunk[String], Validations]] = e.`type` match {
          case "select" | "multiselect" | "Select" | "Multiselect" =>
            val opts = e.options.getOrElse(Seq.empty)
            val values = opts.map(x => x.value)
            e.validations.map(validator => validateValidationsElement(values, validator).toEither)
          case _ =>
            e.validations.map(validator => validateValidationsElement(e.values, validator).toEither)
        }
        println(title)
        println(sectionName)
        println(result)
        val filteredResults: Seq[Either[NonEmptyChunk[String], Validations]] = result.filter(r => r.isLeft)
        val errors: Seq[String] = Seq(title, sectionName, chkType).filter(r => {
          r.isLeft
        }).map(r => r.left.get.mkString(", ")) ++ filteredResults.filter(r => r.isLeft).map(r => {
          r.left.get.mkString(", ")
        })
        println(errors)
        val customerError = errors.mkString(", ")
        allErrors = allErrors :+ customerError
        e.copy(customerError = if (customerError.isEmpty) None else Some(customerError), errors = if (errors.isEmpty) None else Some(errors))
      })
    if (allErrors.isEmpty) Validation.succeed(value.copy(elements = elements)) else Validation.succeed(value.copy(elements = elements, customerError = Some(allErrors.mkString(", "))))
  }
  def validateFormSections(values: Seq[FormSection]):ZValidation[Nothing, FormSection, Seq[FormSection]] = {
    Validation.collectAllPar(values.map(validateFormSection(_)))
  }
}
