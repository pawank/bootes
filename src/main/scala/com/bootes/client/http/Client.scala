package com.bootes.client.http

import com.bootes.config.config.AppConfig
import com.bootes.server.Status
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.ziojson._
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.{Task, ZIO, ZLayer}

final case class Statuses(data: List[Status]) extends AnyVal

object Statuses {
  implicit val codec: JsonCodec[Statuses] = DeriveJsonCodec.gen[Statuses]
}

object Client {
  type Backend = SttpBackend[Task, ZioStreams with WebSockets]

  trait Service {
    def status(headers: Map[String, String]): Task[Statuses]
  }

  def status(headers: Map[String, String]) =
    ZIO.accessM[Client](_.get.status(headers))

  val up = Status.up("proxy")

  val live = ZLayer.fromServices((backend: Backend, conf: AppConfig) =>
    new Service {
      def status(headers: Map[String, String]): Task[Statuses] =
        backend
          .send(
            basicRequest.get(conf.backend.host.withPath("status")).headers(headers).response(asJson[Status])
          )
          .map { response =>
            val status = response.body.getOrElse(Status.down("backend"))
            Statuses(List(status, up))
          }
    }
  )
}
