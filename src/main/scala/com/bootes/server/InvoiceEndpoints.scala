package com.bootes.server

import com.bootes.server.UserServer.{CorrelationId, DebugJsonLog}
import zhttp.http._
import zio.UIO
import zio.logging.log

import java.util.UUID
import scala.runtime.Nothing$

object InvoiceEndpoints {

  val root: Path = Root / "api" / "v1"

  val invoiceRoutes: Http[Any, Nothing, Request, UResponse] = Http.route[Request] {
    case Method.GET -> `root` / _ =>
      invoiceEndpoints(root) +++
        otherEndpoints(root)
    case req: Request             =>
      val root = req.endpoint._2
      Http.succeed(Response.fromHttpError(HttpError.InternalServerError(s"Wait - wut: ${root.path}")))
  }

  private def invoiceEndpoints(root: Path): Http[Any, Nothing, Request, UResponse] = {
    Http.collect[Request] {
      case Method.GET -> `root` / "invoices"                =>
        Response.jsonString("""{"message": "aMessage"}""")
      case Method.GET -> `root` / "invoices" / id / "items" =>
        Response.jsonString(s"""{"message": "another value for $id"}""")
    }
  }

  private def otherEndpoints(root: Path) = Http.collect[Request] { case Method.GET -> `root` / "other" =>
    Response.jsonString("""{"message": "other message"}""")
  }

}
