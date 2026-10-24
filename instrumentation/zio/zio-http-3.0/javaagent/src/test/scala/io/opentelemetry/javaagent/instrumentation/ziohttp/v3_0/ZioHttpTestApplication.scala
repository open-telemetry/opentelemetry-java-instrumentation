/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import zio._
import zio.http._

import java.util.concurrent.CountDownLatch

object ZioHttpTestApplication extends ZIOAppDefault {
  private val routes =
    Routes(
      Method.GET / "greet" / string("name") -> handler {
        (name: String, _: Request) =>
          Response.text(s"Hello $name")
      }
    )

  @volatile var port = -1
  val started = new CountDownLatch(1)

  override def run =
    Server
      .install(routes)
      .flatMap { _ =>
        ZIO.serviceWithZIO[Server](_.port).flatMap { actualPort =>
          ZIO.succeed {
            port = actualPort
            started.countDown()
          } *> ZIO.never
        }
      }
      .provide(Server.defaultWithPort(0))
}
