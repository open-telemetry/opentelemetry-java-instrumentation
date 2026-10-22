/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import zio._
import zio.http._

import java.util.concurrent.CountDownLatch

class ZioHttpTestApplication(port: Int) extends ZIOAppDefault {
  private val routes =
    Routes(
      Method.GET / "greet" / string("name") -> handler {
        (name: String, _: Request) =>
          Response.text(s"Hello $name")
      },
      Method.GET / "greet-async" / string("name") -> handler {
        (name: String, _: Request) =>
          ZIO.yieldNow *> ZIO.succeed(Response.text(s"Hello $name"))
      }
    )

  val started = new CountDownLatch(1)

  override def run = Server
    .install(routes)
    .flatMap { _ =>
      started.countDown()
      ZIO.never
    }
    .provide(Server.defaultWithPort(port))
}
