/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, IllegalUriException}
import org.apache.pekko.stream.Materializer
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.sdk.trace.data.StatusData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

import java.util.concurrent.{CountDownLatch, RejectedExecutionException}
import java.util.concurrent.TimeUnit.SECONDS
import java.util.function.Consumer
import scala.concurrent.Await
import scala.concurrent.duration._

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PekkoClientCallbackTest {
  @RegisterExtension private val testing =
    AgentInstrumentationExtension.create()

  @RegisterExtension private val cleanup = AutoCleanupExtension.create()

  @Test
  def completesRelativeUriFailureWhenAffinityDispatcherIsFull(): Unit = {
    assertRelativeUriFailureWhileDispatcherIsBlocked(
      """
      pekko.actor.default-dispatcher {
        executor = "affinity-pool-executor"
        affinity-pool-executor {
          parallelism-min = 1
          parallelism-max = 1
          task-queue-size = 64
        }
      }
      pekko.loggers-dispatcher = "callback-logger-dispatcher"
      callback-logger-dispatcher {
        type = Dispatcher
        executor = "fork-join-executor"
        fork-join-executor {
          parallelism-min = 1
          parallelism-max = 1
          parallelism-factor = 1.0
        }
      }
      """,
      saturate = true
    )
  }

  @Test
  def completesRelativeUriFailureWhileDispatcherIsBlocked(): Unit = {
    assertRelativeUriFailureWhileDispatcherIsBlocked(
      """
      pekko.actor.default-dispatcher {
        executor = "fork-join-executor"
        fork-join-executor {
          parallelism-min = 1
          parallelism-max = 1
          parallelism-factor = 1.0
        }
      }
      pekko.loggers-dispatcher = "callback-logger-dispatcher"
      callback-logger-dispatcher {
        type = Dispatcher
        executor = "fork-join-executor"
        fork-join-executor {
          parallelism-min = 1
          parallelism-max = 1
          parallelism-factor = 1.0
        }
      }
      """,
      saturate = false
    )
  }

  private def assertRelativeUriFailureWhileDispatcherIsBlocked(
      dispatcherConfig: String,
      saturate: Boolean
  ): Unit = {
    val system = ActorSystem.create(
      "pekko-client-callback",
      ConfigFactory
        .parseString(dispatcherConfig)
        .withFallback(ConfigFactory.load())
    )
    cleanup.deferCleanup(new AutoCloseable {
      override def close(): Unit = Await.result(system.terminate(), 10.seconds)
    })
    val dispatcher = system.dispatcher
    implicit val materializer: Materializer =
      Materializer(system)
    val http = Http.get(system)

    val workerStarted = new CountDownLatch(1)
    val releaseWorker = new CountDownLatch(1)
    try {
      dispatcher.execute(new Runnable {
        override def run(): Unit = {
          workerStarted.countDown()
          releaseWorker.await()
        }
      })
      assertThat(workerStarted.await(10, SECONDS)).isTrue()

      if (saturate) {
        var saturated = false
        while (!saturated) {
          try {
            dispatcher.execute(new Runnable {
              override def run(): Unit = ()
            })
          } catch {
            case _: RejectedExecutionException => saturated = true
          }
        }
      }

      val response = http.singleRequest(HttpRequest(uri = "/relative"))
      val failure = Await.result(response.failed, 5.seconds)
      assertThat(failure).isInstanceOf(classOf[IllegalUriException])
      assertThat(response.isCompleted).isTrue()

      testing.waitAndAssertTraces(new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit =
              span
                .hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasStatus(StatusData.error())
                .hasException(failure)
          })
      })
    } finally {
      releaseWorker.countDown()
    }
  }
}
