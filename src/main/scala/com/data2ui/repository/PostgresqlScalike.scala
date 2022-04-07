package com.data2ui.repository

import java.util.UUID

object PostgresqlScalike {
  
}

import scalikejdbc._
import java.time.ZonedDateTime
import java.sql.Timestamp
import shapeless.record
import com.data2ui.models.Models.Submission
import scala.collection.JavaConverters._

case class DatabaseConfig(hostname: String, port: Int, name: String)
case class Credential(username: String, password: String)


object PostgresqlScalikeJdbc {
	val database: DatabaseConfig = DatabaseConfig(sys.env.getOrElse("RAPIDOR_DB_HOSTNAME", "localhost"), sys.env.getOrElse("RAPIDOR_DB_PORT", "5432").toInt, "")
	val credential: Credential = Credential(username = sys.env.getOrElse("DB_USER=postgres", "postgres"), password = sys.env.getOrElse("DB_PASSWORD", ""))

	def makeConnectionPool(databaseName: String) = {
		// initialize JDBC driver & connection pool
		Class.forName("org.postgresql.Driver")
		ConnectionPool.singleton(s"jdbc:postgresql://${database.hostname}:${database.port}/${databaseName}", credential.username, credential.password)
	}
}

object Queries {
	val RAPIDOR_MASTER_DB = sys.env.getOrElse("DB_NAME", "")

	GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
  enabled = true,
  singleLineMode = false,
  printUnprocessedStackTrace = false,
  stackTraceDepth= 15,
  logLevel = Symbol("debug"),
  warningEnabled = false,
  warningThresholdMillis = 3000L,
  warningLogLevel = Symbol("warn")
)

	def asInt(value: Option[Any], default: Int = 0): Int = {
		value match {
			case Some(value) =>
		scala.util.Try(value.asInstanceOf[Int]) match {
			case scala.util.Success(v) => v
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				default
		}
				case _ => default
		}
	}
	def asDouble(value: Option[Any]): Double = {
		value match {
			case Some(value) =>
		scala.util.Try(value.asInstanceOf[java.math.BigDecimal]) match {
			case scala.util.Success(v) => v.doubleValue()
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				0.0d
		}
				case _ => 0.0d
		}
	}

	def asDouble(value: Option[Any], default: Double): Double = {
		value match {
			case Some(value) =>
		scala.util.Try(value.asInstanceOf[Double]) match {
			case scala.util.Success(v) => v
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				default
		}
				case _ => default
		}
	}
	def asFloat(value: Option[Any]): Float = {
		value match {
			case Some(value) =>
		scala.util.Try(value.asInstanceOf[java.math.BigDecimal]) match {
			case scala.util.Success(v) => v.doubleValue().toFloat
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				0.0f
		}
				case _ => 0.0f
		}
	}
	def asFloat(value: Option[Any], default: Float = 0.0f): Float = {
		value match {
			case Some(value) =>
		scala.util.Try(value.asInstanceOf[Float]) match {
			case scala.util.Success(v) => v
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				default
		}
				case _ => default
		}
	}
	def asBoolean(value: Option[Any], default: Boolean = false): Boolean = {
		value match {
			case Some(value) =>
		scala.util.Try(value.asInstanceOf[Boolean]) match {
			case scala.util.Success(v) => v
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				default
		}
				case _ => default
		}
	}
	def asUUID(value: Option[Any], default: UUID = UUID.randomUUID()): UUID = {
		value match {
			case Some(value) =>
				//println(s"v = $value\n")
				scala.util.Try(value.asInstanceOf[UUID]) match {
			case scala.util.Success(v) => v
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				default
		}
				case _ => default
		}
	}
	def asArray(value: Option[Any], default: Array[String] = Array.empty): Array[String] = {
		value match {
			case Some(value) =>
				scala.util.Try(value.asInstanceOf[Array[String]]) match {
			case scala.util.Success(v) => v
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				default
		}
				case _ => default
		}
	}
	def asString(value: Option[Any], default: String = ""): String = {
		value match {
			case Some(value) =>
				//println(s"v = $value\n")
				scala.util.Try(value.asInstanceOf[String]) match {
			case scala.util.Success(v) => v
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				default
		}
				case _ => default
		}
	}
	def asOptTimestamp(value: Option[Any], default: Option[Timestamp] = None): Option[Timestamp] = {
		value match {
			case Some(value) =>
		scala.util.Try(value.asInstanceOf[Timestamp]) match {
			case scala.util.Success(v) => Some(v)
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				default
		}
				case _ => default
		}
	}

  def getQueryData(id: Option[UUID], status: String) = id match {
	  case Some(formid) =>
	  sql"""select a.id,a.template_id,a.title,a.sub_title,a.status,a.created_at as form_created_on,a.created_by,b.id as submission_id,b.seq_no,b.name as element_name,b.title as element_title,b.values,b.type,b.section_name,b.section_seq_no,b.created_at as submitted_on from form as a left join form_element as b on a.id=b.form_id where (a.id=$formid OR a.template_id=$formid) and b.id is not null and a.status=$status order by b.section_seq_no,b.seq_no"""
	  //sql"""select a.id,a.template_id,a.title,a.sub_title,a.status,a.created_at as form_created_on,a.created_by,b.id as submission_id,b.seq_no,b.name as element_name,b.title as element_title,b.values,b.type,b.section_name,b.section_seq_no,b.created_at as submitted_on from form as a left join form_element as b on a.id=b.form_id where a.template_id=$formid and b.id is not null and a.status=$status order by b.section_seq_no,b.seq_no"""
		case _ =>
	  sql"""select a.id,a.template_id,a.title,a.sub_title,a.status,a.created_at as form_created_on,a.created_by,b.id as submission_id,b.seq_no,b.name as element_name,b.title as element_title,b.values,b.type,b.section_name,b.section_seq_no,b.created_at as submitted_on from form as a left join form_element as b on a.id=b.form_id where a.template_id is not null and b.id is not null and a.status=$status order by b.section_seq_no,b.seq_no"""
  }
  
  def getSubmissions(databaseName: String, id: Option[UUID], status: String): List[Submission] = {
		PostgresqlScalikeJdbc.makeConnectionPool(databaseName)	
	  val records = DB localTx {implicit session =>
		val xs: List[Option[Submission]] = getQueryData(id, status).map(x => {
			val form_id = asUUID(x.anyOpt("id"))
			//println(s"id = $form_id")
			try {
			val title = asString(x.anyOpt("title"))
			val sub_title = asString(x.anyOpt("sub_title"))
			val status = asString(x.anyOpt("status"))
			val created_by = asString(x.anyOpt("created_by"))
			val submission_id = asUUID(x.anyOpt("submission_id"))
			val template_id: Option[UUID] = {
				val tmp = asUUID(x.anyOpt("template_id"))
				//println(s"tmp = $tmp")
				if (tmp == null) None else Some(tmp)
			}
			if (template_id.isEmpty && x.anyOpt("element_name").isEmpty && x.anyOpt("element_title").isEmpty) {
					println(s"Form ID = $form_id has error because no elements are found and no mapped template id exists")
				None
			} else {
				val seq_no = asInt(x.anyOpt("seq_no"))
				val element_name = asString(x.anyOpt("element_name"))
				val element_title = asString(x.anyOpt("element_title"))
				//println(s"ele = $element_title")
				val values: Array[String] = x.array("values").getArray.asInstanceOf[Array[AnyRef]].map(i => if (i != null) i.asInstanceOf[String] else "")
				//println(s"values = $values")
				val value: String = values.mkString(",")
				val `type` = asString(x.anyOpt("type"))
				val section_name = asString(x.anyOpt("section_name"))
				val section_seq_no = asInt(x.anyOpt("section_seq_no"))
				//println(s"section = $section_seq_no")
				val submitted_on = asOptTimestamp(x.anyOpt("submitted_on"))
				//println(s"submitted on = $submitted_on")
				val form_created_on = asOptTimestamp(x.anyOpt("form_created_on"))
					Option(Submission(id = form_id, template_id = template_id, title = title, sub_title = sub_title, status = status, form_created_on = com.bootes.utils.Utils.asZonedDateTimeOpt(form_created_on).getOrElse(ZonedDateTime.now()), created_by = Option(created_by), 
				element_id = Option(submission_id), seq_no = seq_no, element_name = element_name, element_title = element_title, values = values, value = if (values.isEmpty) None else Some(value), field_type = `type`, section_name = Option(section_name), section_seq_no = section_seq_no, 
				submitted_on = com.bootes.utils.Utils.asZonedDateTimeOpt(submitted_on), submitted_by = None))

			}
			} catch {
				case e: Exception =>
					e.printStackTrace()
					println(s"Form ID = $form_id has error while getting submissions")
					None
			}
		}).list().apply()
		//println(s"Records = $xs")
		xs
	}
		records.filter(_.isDefined).map(_.get)
  }


}

