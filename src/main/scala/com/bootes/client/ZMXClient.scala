package com.bootes.client

import zio._
import zio.console._
import zio.zmx._
import zio.zmx.diagnostics._
object ZMXClientApp extends scala.App {
  val zmxConfig = ZMXConfig(host = "localhost", port = 1111, debug = false) // or `ZMXConfig.empty` for defaults // or `ZMXConfig.empty` for defaults
  val zmxClient = new ZMXClient(zmxConfig)

  val program: ZIO[Any with Console, Throwable, Unit] =
    for {
      _      <- putStrLn("Type command to send:")
      rawCmd <- getStrLn
      cmd    <- if (Set("dump", "test") contains rawCmd) ZIO.succeed(rawCmd)
      else ZIO.fail(new RuntimeException("Invalid command"))

      resp <- zmxClient.sendCommand(Chunk(cmd))
      _    <- putStrLn("Diagnostics returned response:")
      r    <- putStrLn(resp)
    } yield ()

  val runtime: Runtime[ZEnv] =
    Runtime.default.mapPlatform(_.withSupervisor(ZMXSupervisor))

  def run(args: Array[String]): Unit = {
    //program.provideCustomLayer(Console.live).run.exitCode
    val prog = program.catchAll(e => putStrLn(s"${e.getMessage}. Quiting.."))
    runtime.unsafeRun(prog)
  }
}
