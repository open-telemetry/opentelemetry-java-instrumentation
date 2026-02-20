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
