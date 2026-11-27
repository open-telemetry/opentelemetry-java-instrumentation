/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.{Test, TestInstance}
import zio._

import java.util.concurrent.{
  CountDownLatch,
  ExecutorService,
  Executors,
  TimeUnit
}
import java.util.concurrent.atomic.AtomicReference

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZioScopeLifecycleTest {

  @RegisterExtension
  val testing: InstrumentationExtension = AgentInstrumentationExtension.create()

  @Test
  def suspensionWhileApplicationScopeIsOpenDoesNotLeakRestorationScope()
      : Unit = {
    val worker = Executors.newSingleThreadExecutor()
    val executor = Executor.fromJavaExecutor(worker)
    val span = testing.getOpenTelemetry
      .getTracer("test")
      .spanBuilder("application")
      .startSpan()
    try {
      run {
        ZIO
          .acquireReleaseWith(ZIO.succeed(span.makeCurrent()))(scope =>
            ZIO.succeed(scope.close())
          ) { _ =>
            ZIO.yieldNow *> ZIO.succeed {
              assertThat(Span.current().getSpanContext)
                .isEqualTo(span.getSpanContext)
            }
          }
          .onExecutor(executor)
      }
      worker
        .submit(new Runnable {
          override def run(): Unit = ()
        })
        .get(10, TimeUnit.SECONDS)
      ContextStorage.get().asInstanceOf[AutoCloseable].close()
    } finally {
      span.end()
      shutdown(worker)
    }
  }

  @Test
  def applicationScopeCanCloseOnDifferentWorkerAfterSuspension(): Unit = {
    val openingWorker = Executors.newSingleThreadExecutor()
    val closingWorker = Executors.newSingleThreadExecutor()
    val openingExecutor = Executor.fromJavaExecutor(openingWorker)
    val closingExecutor = Executor.fromJavaExecutor(closingWorker)
    val span = testing.getOpenTelemetry
      .getTracer("test")
      .spanBuilder("application")
      .startSpan()
    try {
      run {
        ZIO
          .acquireReleaseWith(
            ZIO.succeed((span.makeCurrent(), Thread.currentThread()))
          ) { case (scope, _) =>
            ZIO.succeed(scope.close()).onExecutor(openingExecutor)
          } { case (scope, openingThread) =>
            (ZIO.yieldNow *> ZIO.succeed {
              assertThat(Thread.currentThread()).isNotSameAs(openingThread)
              assertThat(Span.current().getSpanContext).isEqualTo(
                span.getSpanContext
              )
              scope.close()
            }).onExecutor(closingExecutor)
          }
          .onExecutor(openingExecutor)
      }
    } finally {
      span.end()
      try {
        shutdown(openingWorker)
      } finally {
        shutdown(closingWorker)
      }
    }
  }

  @Test
  def fiberScopesEnforceNestingAndOwnership(): Unit = {
    val tracer = testing.getOpenTelemetry.getTracer("test")
    val outerSpan = tracer
      .spanBuilder("application")
      .startSpan()
    val innerSpan = tracer.spanBuilder("inner").startSpan()
    try {
      run {
        for {
          outer <- ZIO.succeed(outerSpan.makeCurrent())
          inner <- ZIO.succeed(innerSpan.makeCurrent())
          nestingRejected <- ZIO.succeed {
            try {
              outer.close()
              false
            } catch {
              case _: IllegalStateException => true
            }
          }
          _ <- ZIO.succeed(assertThat(nestingRejected).isTrue())
          closeFromChild <- ZIO.attempt(inner.close()).either.fork
          childResult <- closeFromChild.join
          _ <- ZIO.succeed(assertThat(childResult.isLeft).isTrue())
          _ <- ZIO.succeed(inner.close())
          _ <- ZIO.succeed(outer.close())
        } yield ()
      }
    } finally {
      innerSpan.end()
      outerSpan.end()
    }
  }

  @Test
  def openFiberScopesAreDetectedByContextStorageClose(): Unit = {
    val span = testing.getOpenTelemetry
      .getTracer("test")
      .spanBuilder("application")
      .startSpan()
    try {
      run {
        ZIO
          .acquireReleaseWith(ZIO.succeed(span.makeCurrent()))(scope =>
            ZIO.succeed(scope.close())
          ) { _ =>
            ZIO.succeed {
              var detected = false
              try {
                ContextStorage.get().asInstanceOf[AutoCloseable].close()
              } catch {
                case _: AssertionError => detected = true
              }
              assertThat(detected).isTrue()
              ()
            }
          }
      }
    } finally {
      span.end()
    }
  }

  @Test
  def completionObserverSeesInheritedContextAfterFiberYields(): Unit = {
    val worker = Executors.newSingleThreadExecutor()
    val executor = Executor.fromJavaExecutor(worker)
    val span = testing.getOpenTelemetry
      .getTracer("test")
      .spanBuilder("application")
      .startSpan()
    val observedSpanContext =
      new AtomicReference[io.opentelemetry.api.trace.SpanContext]()
    val observerCalled = new CountDownLatch(1)
    val scope = span.makeCurrent()
    var scopeClosed = false
    try {
      run {
        (for {
          release <- Promise.make[Nothing, Unit]
          fiber <- (release.await *> ZIO.yieldNow *> ZIO.fail(
            new RuntimeException("failure")
          )).fork
          _ <- ZIO.succeed {
            Unsafe.unsafe { implicit unsafe =>
              fiber
                .asInstanceOf[Fiber.Runtime[Throwable, Nothing]]
                .unsafe
                .addObserver(_ => {
                  observedSpanContext.set(Span.current().getSpanContext)
                  observerCalled.countDown()
                })
            }
          }
          _ <- release.succeed(())
          _ <- fiber.await
        } yield ()).onExecutor(executor)
      }

      assertThat(observerCalled.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(observedSpanContext.get()).isEqualTo(span.getSpanContext)
      assertThat(Span.current().getSpanContext).isEqualTo(span.getSpanContext)
      scope.close()
      scopeClosed = true
      worker
        .submit(new Runnable {
          override def run(): Unit =
            assertThat(Span.current().getSpanContext.isValid).isFalse()
        })
        .get(10, TimeUnit.SECONDS)
    } finally {
      if (!scopeClosed) {
        scope.close()
      }
      span.end()
      shutdown(worker)
    }
  }

  private def run[A](effect: UIO[A]): Unit =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          effect.timeoutFailCause(
            Cause.die(new AssertionError("Fiber did not complete"))
          )(Duration.fromSeconds(10))
        )
        .getOrThrowFiberFailure()
      ()
    }

  private def shutdown(executor: ExecutorService): Unit = {
    executor.shutdownNow()
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
  }
}
