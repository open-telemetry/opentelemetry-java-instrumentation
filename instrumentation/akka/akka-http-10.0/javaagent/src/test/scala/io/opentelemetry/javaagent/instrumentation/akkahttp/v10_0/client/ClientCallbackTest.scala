/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.testing.internal.armeria.common.{
  HttpRequest => ServerHttpRequest,
  HttpResponse => ServerHttpResponse
}
import io.opentelemetry.testing.internal.armeria.server.{
  HttpService,
  Server,
  ServiceRequestContext
}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.{Test, TestInstance}
import org.junit.jupiter.api.extension.RegisterExtension

import java.util.concurrent.{
  CompletableFuture,
  CountDownLatch,
  RejectedExecutionException
}
import java.util.concurrent.TimeUnit.SECONDS
import java.util.function.Consumer
import scala.concurrent.Await
import scala.concurrent.duration._

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientCallbackTest {
  @RegisterExtension private val testing =
    AgentInstrumentationExtension.create()

  @RegisterExtension private val cleanup = AutoCleanupExtension.create()

  @Test
  def completesPendingResponseWhenAffinityDispatcherIsFull(): Unit = {
    // The baseline Akka 2.4 release has no affinity-pool executor.
    assumeTrue(testLatestDeps())

    val requestReceived = new CompletableFuture[Void]()
    val serverResponse = new CompletableFuture[ServerHttpResponse]()
    val server = Server
      .builder()
      .http(0)
      .service(
        "/callback",
        new HttpService {
          override def serve(
              ctx: ServiceRequestContext,
              req: ServerHttpRequest
          ): ServerHttpResponse = {
            requestReceived.complete(null)
            ServerHttpResponse.of(serverResponse)
          }
        }
      )
      .build()
    cleanup.deferCleanup(new AutoCloseable {
      override def close(): Unit = server.stop().join()
    })
    server.start().join()

    val system = ActorSystem.create(
      "client-callback",
      ConfigFactory
        .parseString(
          """
          akka.actor.default-dispatcher {
            executor = "affinity-pool-executor"
            affinity-pool-executor {
              parallelism-min = 1
              parallelism-max = 1
              task-queue-size = 64
            }
          }
          # Keep HTTP streams and logging runnable during default-dispatcher overload.
          http-dispatcher {
            type = Dispatcher
            executor = "fork-join-executor"
          }
          akka.stream.materializer.dispatcher = http-dispatcher
          akka.loggers-dispatcher = http-dispatcher
          """
        )
        .withFallback(ConfigFactory.load())
    )
    cleanup.deferCleanup(new AutoCloseable {
      override def close(): Unit = Await.result(system.terminate(), 10.seconds)
    })
    val dispatcher = system.dispatcher
    implicit val materializer: ActorMaterializer =
      ActorMaterializer.create(system)

    val response = Http
      .get(system)
      .singleRequest(
        HttpRequest(
          uri = "http://localhost:" + server.activeLocalPort() + "/callback"
        )
      )

    requestReceived.get(10, SECONDS)
    assertThat(response.isCompleted).isFalse()

    val workerStarted = new CountDownLatch(1)
    val releaseWorker = new CountDownLatch(1)
    dispatcher.execute(new Runnable {
      override def run(): Unit = {
        workerStarted.countDown()
        releaseWorker.await()
      }
    })
    try {
      assertThat(workerStarted.await(10, SECONDS)).isTrue()
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
      serverResponse.complete(ServerHttpResponse.of("response body"))
      val result = Await.result(response, 10.seconds)
      assertThat(result.status.intValue()).isEqualTo(200)
      assertThat(
        Await
          .result(result.entity.toStrict(5.seconds), 10.seconds)
          .data
          .utf8String
      ).isEqualTo("response body")

      testing.waitAndAssertTraces(new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit =
              span
                .hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasStatus(StatusData.unset())
                .hasEventsSatisfyingExactly()
          })
      })
    } finally {
      releaseWorker.countDown()
    }
  }
}
