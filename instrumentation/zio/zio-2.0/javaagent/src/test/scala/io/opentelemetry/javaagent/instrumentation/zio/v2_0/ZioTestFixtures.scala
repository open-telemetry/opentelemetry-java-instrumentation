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
        _ <- childSpan("fiber_1_span_1") {
          for {
            child <- childSpan("fiber_2_span_1") {
              childStarted.succeed(()) *>
                ZIO.never
            }.fork
            _ <- childStarted.await
            _ <- child.interrupt
          } yield ()
        }
      } yield ()
    }

  def runSynchronizedFibers(): Unit = {
    def runFiber(
        fiberNumber: Int,
        onStart: UIO[Unit],
        onEnd: UIO[Unit]
    ): ZIO[Any, Nothing, Any] =
      childSpan(s"fiber_${fiberNumber}_span_1") {
        onStart *>
          childSpan(s"fiber_${fiberNumber}_span_2") {
            onEnd
          }
      }

    run {
      for {
        fiber1Started <- Promise.make[Nothing, Unit]
        fiber2Done <- Promise.make[Nothing, Unit]

        fiber1 <- runFiber(
          fiberNumber = 1,
          onStart = fiber1Started.succeed(()) *> fiber2Done.await,
          onEnd = ZIO.unit
        ).fork

        fiber2 <- runFiber(
          fiberNumber = 2,
          onStart = fiber1Started.await,
          onEnd = fiber2Done.succeed(()).unit
        ).fork

        _ <- Fiber.joinAll(List(fiber1, fiber2))
      } yield ()
    }
  }

  def runConcurrentFibers(): Unit = {
    def runFiber(
        fiberNumber: Int,
        start: Promise[Nothing, Unit]
    ): ZIO[Any, Nothing, Unit] = {
      start.await *>
        childSpan(s"fiber_${fiberNumber}_span_1") {
          ZIO.yieldNow *>
            childSpan(s"fiber_${fiberNumber}_span_2") {
              ZIO.yieldNow
            }
        }
    }

    run {
      for {
        start <- Promise.make[Nothing, Unit]
        fiber1 <- runFiber(1, start).fork
        fiber2 <- runFiber(2, start).fork
        fiber3 <- runFiber(3, start).fork
        _ <- start.succeed(())
        _ <- Fiber.joinAll(List(fiber1, fiber2, fiber3))
      } yield ()
    }
  }

  def runSequentialFibers(): Unit = {
    def runFiber(fiberNumber: Int): ZIO[Any, Nothing, Unit] = {
      childSpan(s"fiber_${fiberNumber}_span_1") {
        childSpan(s"fiber_${fiberNumber}_span_2") {
          ZIO.unit
        }
      }
    }

    run {
      for {
        fiber1 <- runFiber(1).fork
        _ <- fiber1.join
        fiber2 <- runFiber(2).fork
        _ <- fiber2.join
        fiber3 <- runFiber(3).fork
        _ <- fiber3.join
      } yield ()
    }
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
    val zioExecutor = Executor.fromJavaExecutor(executor)
    val layer =
      Runtime.setExecutor(zioExecutor) >>>
        Runtime.setBlockingExecutor(zioExecutor)
    try {
      Unsafe.unsafe { implicit unsafe =>
        Runtime.unsafe
          .fromLayer(layer)
          .unsafe
          .run(zio)
          .getOrThrowFiberFailure()
      }
    } finally {
      executor.shutdownNow()
    }
  }

}
