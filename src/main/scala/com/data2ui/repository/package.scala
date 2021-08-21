package com.data2ui.repository

import io.getquill.context.jdbc.JdbcRunContext
import io.getquill.{NamingStrategy, PostgresZioJdbcContext, SnakeCase}
import zio.json.{DeriveJsonCodec, JsonCodec}

import java.sql.{Timestamp, Types}
import java.time.{Instant, ZoneId, ZonedDateTime}

package object repository {
  import java.nio.charset.Charset

  class FormZioContext[N <: NamingStrategy](override val naming: N) extends PostgresZioJdbcContext[N](naming) with InstantEncoding

  object FormContext extends PostgresZioJdbcContext(SnakeCase) with InstantEncoding

  //noinspection DuplicatedCode
  trait InstantEncoding { this: JdbcRunContext[_, _] =>
    implicit val instantDecoder: Decoder[Instant] = decoder((index, row) => { row.getTimestamp(index).toInstant })
    implicit val instantEncoder: Encoder[Instant] = encoder(Types.TIMESTAMP, (idx, value, row) => row.setTimestamp(idx, Timestamp.from(value)))
    implicit val zoneDateTimeEncoder: Encoder[ZonedDateTime] = encoder(Types.TIMESTAMP_WITH_TIMEZONE, (index, value, row) => row.setTimestamp(index, Timestamp.from(value.toInstant)))
    implicit val zoneDateTimeDecoder: Decoder[ZonedDateTime] = decoder((index, row) => { ZonedDateTime.ofInstant(row.getTimestamp(index).toInstant, ZoneId.of("UTC")) })
  }
  case class NotFoundException(message: String, value: String) extends Throwable
}
