/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import zio._
import zio.http._

import java.util.concurrent.CountDownLatch

class ZioHttpTestApplication(port: Int) {
  private val delegatedRoutes =
    Routes(
      Method.GET / "delegate" / "test" -> handler { (_: Request) =>
        Response.text("Inner test")
      }
    )

  private val middleDelegatedRoutes =
    Routes(
      Method.GET / "delegate" / string("middle") -> handler {
        (_: String, request: Request) =>
          delegatedRoutes.runZIO(request)
      }
    )

  private val routes =
    Routes(
      Method.GET / "greet" / string("name") -> handler {
        (name: String, _: Request) =>
          Response.text(s"Hello $name")
      },
      Method.GET / "greet-async" / string("name") -> handler {
        (name: String, _: Request) =>
          ZIO.yieldNow *> ZIO.succeed(Response.text(s"Hello $name"))
      },
      Method.GET / "delegate" / string("name") -> handler {
        (_: String, request: Request) =>
          if (
            request.rawHeader("x-test-yield-before-delegation").contains("true")
          )
            ZIO.yieldNow *> ZIO.suspendSucceed {
              if (request.rawHeader("x-test-deep-delegation").contains("true"))
                middleDelegatedRoutes.runZIO(request)
              else delegatedRoutes.runZIO(request)
            }
          else if (request.rawHeader("x-test-deep-delegation").contains("true"))
            middleDelegatedRoutes.runZIO(request)
          else delegatedRoutes.runZIO(request)
      },
      Method.GET / "failure" / string("name") -> handler {
        (_: String, _: Request) =>
          ZIO.yieldNow *> ZIO.fail(Response.internalServerError)
      }
    )

  val started = new CountDownLatch(1)

  def run = Server
    .install(routes)
    .flatMap { _ =>
      started.countDown()
      ZIO.never
    }
    .provide(Server.defaultWithPort(port))
}
