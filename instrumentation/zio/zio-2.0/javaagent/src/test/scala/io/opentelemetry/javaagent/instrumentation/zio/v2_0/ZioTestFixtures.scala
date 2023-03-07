/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import zio._

import java.util.concurrent.Executors

object ZioTestFixtures {

  def runNestedFibers(): Unit =
    run {
      childSpan("fiber_1_span_1") {
        for {
          child <- childSpan("fiber_2_span_1")(ZIO.unit).fork
          _ <- child.join
        } yield ()
      }
    }

  def runInterruptedFiber(): Unit =
    run {
      for {
        childStarted <- Promise.make[Nothing, Unit]
        never <- Promise.make[Nothing, Unit]
        _ <- childSpan("fiber_1_span_1") {
          for {
            child <- childSpan("fiber_2_span_1") {
              childStarted.succeed(()) *>
                never.await
            }.fork
            _ <- childStarted.await
            _ <- child.interrupt
          } yield ()
        }
      } yield ()
    }

  def runConcurrentFibers(): Unit =
    run {
      for {
        fiber1Started <- Promise.make[Nothing, Unit]
        fiber2Done <- Promise.make[Nothing, Unit]

        fiber1 <- childSpan("fiber_1_span_1") {
          fiber1Started.succeed(()) *>
            fiber2Done.await *>
            childSpan("fiber_1_span_2")(ZIO.unit)
        }.fork

        fiber2 <- childSpan("fiber_2_span_1") {
          fiber1Started.await *>
            childSpan("fiber_2_span_2") {
              fiber2Done.succeed(()) *>
                ZIO.unit
            }
        }.fork

        _ <- Fiber.joinAll(List(fiber1, fiber2))
      } yield ()
    }

  private val tracer: Tracer = GlobalOpenTelemetry.getTracer("test")

  private def childSpan(opName: String)(op: UIO[Unit]): UIO[Unit] =
    ZIO.scoped {
      for {
        scope <- ZIO.scope
        otelSpan <- ZIO.succeed(tracer.spanBuilder(opName).startSpan())
        otelScope <- ZIO.succeed(otelSpan.makeCurrent())
        _ <- scope.addFinalizer(ZIO.succeed(otelSpan.end()))
        _ <- scope.addFinalizer(ZIO.succeed(otelScope.close()))
        _ <- op
      } yield ()
    }

  private def run[A](zio: ZIO[Any, Nothing, A]): Unit = {
    val executor = Executors.newSingleThreadExecutor()
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run {
          ZIO
            .scoped {
              for {
                _ <- Runtime
                  .setExecutor(Executor.fromJavaExecutor(executor))
                  .build
                _ <- Runtime
                  .setBlockingExecutor(Executor.fromJavaExecutor(executor))
                  .build
                res <- zio
              } yield res
            }
            .onExecutor(Executor.fromJavaExecutor(executor))
        }
        .getOrThrowFiberFailure()
    }
  }

}
