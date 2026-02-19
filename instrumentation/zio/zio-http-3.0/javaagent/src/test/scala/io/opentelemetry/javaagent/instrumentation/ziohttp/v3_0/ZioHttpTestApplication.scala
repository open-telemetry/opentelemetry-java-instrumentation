/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import zio._
import zio.http._

import java.util.concurrent.CountDownLatch

object ZioHttpTestApplication extends ZIOAppDefault {
  val routes =
    Routes(
      Method.GET / "greet" / string("name") -> handler {
        (name: String, _: Request) =>
          Response.text(s"Hello $name")
      }
    )
  // def run = Server.serve(routes).provide(Server.default)
  // ZLayer.succeed(Server.Config.default.port(8080))

  def start(port: Int): Unit = {
    new Exception().printStackTrace()
    this.port = port
    run
    /*
    Server.serve(routes)
      .flatMap { port =>
        Console.printLine(s"Started on $port") *> ZIO.never
      }
      .provide(Server.defaultWithPort(port))
     */
  }

  var port = -1
  val started = new CountDownLatch(1)

  override def run = Server
    .serve(routes)
    .flatMap { _ =>
      started.countDown()
      ZIO.never
    }
    .provide(Server.defaultWithPort(port))
}
