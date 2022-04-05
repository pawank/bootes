package com.bootes.utils

import java.util.Properties
import java.sql.Timestamp
import java.time.ZonedDateTime
import scala.util.Try
import java.time.ZoneId

object Utils {
	def toProperties(map: Map[String, String]): java.util.Properties = {
		val properties = new Properties
		map.foreach { 
			case (key, value) => properties.setProperty(key, value.toString) 
		}
		properties
	}

	def timestampToDateTime(timestamp: Timestamp): Option[ZonedDateTime] = {
		Try({
			val ts: ZonedDateTime = timestamp.toLocalDateTime().atZone(ZoneId.systemDefault())
			ts
		}) match {
			case scala.util.Success(v) => Some(v)
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				None
		}
	}
	def asZonedDateTimeOpt(timestamp: Option[Timestamp]): Option[ZonedDateTime] = timestamp match {
		case Some(ts) => timestampToDateTime(ts)
		case _ => None
	}

	def datetimeToTimestamp(timestamp: ZonedDateTime): Option[Timestamp] = {
		Try({
			val ts: Timestamp = Timestamp.valueOf(timestamp.toLocalDateTime())
			ts
		}) match {
			case scala.util.Success(v) => Some(v)
			case scala.util.Failure(ex) =>
				ex.printStackTrace()
				None
		}
	}

	//def asTimestampOpt(datetime: Option[ZonedDateTime]): Option[ZonedDateTime] = datetime match {
	//	case Some(dt) => datetimeToTimestamp(dt)
	//	case _ => None
	//}
}

