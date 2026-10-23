/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import zio._
import zio.http._

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ZioHttpTestApplication(port: Int) {
  private val failReusedRequest = new AtomicBoolean()
  private val reusedRequest = Request.get("/reused/test")
  private val firstOverlapEntered = new CountDownLatch(1)
  private val secondOverlapEntered = new CountDownLatch(1)
  private val releaseFirstOverlap = new CountDownLatch(1)

  private val reusedRoutes =
    Routes(
      Method.GET / "reused" / "test" -> handler { (_: Request) =>
        if (failReusedRequest.compareAndSet(true, false))
          ZIO.yieldNow *> ZIO.fail(Response.internalServerError)
        else ZIO.succeed(Response.text("Reused inner test"))
      }
    )

  private val firstOverlapRoutes =
    Routes(
      Method.GET / "reused" / string("name") -> handler {
        (_: String, _: Request) =>
          ZIO.succeed(firstOverlapEntered.countDown()) *>
            ZIO.attemptBlocking {
              if (!releaseFirstOverlap.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException(
                  "Timed out waiting to release first request"
                )
              Response.text("First inner test")
            }.orDie
      }
    )

  private val secondOverlapRoutes =
    Routes(
      Method.GET / string("name") / "test" -> handler {
        (_: String, _: Request) =>
          ZIO.succeed(secondOverlapEntered.countDown()) *>
            ZIO.succeed(Response.text("Second inner test"))
      }
    )

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
          ZIO.scoped(delegatedRoutes.runZIO(request))
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
      Method.GET / "reuse" / string("mode") -> handler {
        (mode: String, _: Request) =>
          mode match {
            case "overlap-first" =>
              ZIO.scoped(firstOverlapRoutes.runZIO(reusedRequest))
            case "overlap-second" =>
              ZIO.scoped(secondOverlapRoutes.runZIO(reusedRequest))
            case _ =>
              failReusedRequest.set(mode == "failure")
              ZIO.scoped(reusedRoutes.runZIO(reusedRequest))
          }
      },
      Method.GET / "delegate" / string("name") -> handler {
        (_: String, request: Request) =>
          if (
            request.rawHeader("x-test-yield-before-delegation").contains("true")
          )
            ZIO.yieldNow *> ZIO.suspendSucceed {
              if (request.rawHeader("x-test-deep-delegation").contains("true"))
                ZIO.scoped(middleDelegatedRoutes.runZIO(request))
              else ZIO.scoped(delegatedRoutes.runZIO(request))
            }
          else if (request.rawHeader("x-test-deep-delegation").contains("true"))
            ZIO.scoped(middleDelegatedRoutes.runZIO(request))
          else ZIO.scoped(delegatedRoutes.runZIO(request))
      },
      Method.GET / "failure" / string("name") -> handler {
        (_: String, _: Request) =>
          ZIO.yieldNow *> ZIO.fail(Response.internalServerError)
      }
    )

  val started = new CountDownLatch(1)

  def awaitFirstOverlap(): Boolean =
    firstOverlapEntered.await(10, TimeUnit.SECONDS)

  def awaitSecondOverlap(): Boolean =
    secondOverlapEntered.await(10, TimeUnit.SECONDS)

  def releaseOverlap(): Unit = releaseFirstOverlap.countDown()

  def run: ZIO[Any, Throwable, Nothing] = Server
    .install(routes)
    .flatMap { _ =>
      started.countDown()
      ZIO.never
    }
    .provide(Server.defaultWithPort(port))
}
