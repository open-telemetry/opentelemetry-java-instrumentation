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
import org.junit.jupiter.api.{Disabled, Test, TestInstance}
import zio._

import java.util.concurrent.{ExecutorService, Executors, TimeUnit}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZioScopeLifecycleTest {

  @RegisterExtension
  val testing: InstrumentationExtension = AgentInstrumentationExtension.create()

  @Test
  @Disabled(
    "Suspension discards an unclosed restoration scope: https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/14938"
  )
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
      // Drain the worker's final onSuspend callback before checking for leaked scopes.
      worker
        .submit(new Runnable {
          override def run(): Unit = ()
        })
        .get(10, TimeUnit.SECONDS)
      // Check immediately: the extension retries, allowing discarded scopes to be garbage collected.
      ContextStorage.get().asInstanceOf[AutoCloseable].close()
    } finally {
      span.end()
      shutdown(worker)
    }
  }

  @Test
  @Disabled(
    "Application scopes cannot close on a different worker after suspension: https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/14938"
  )
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
