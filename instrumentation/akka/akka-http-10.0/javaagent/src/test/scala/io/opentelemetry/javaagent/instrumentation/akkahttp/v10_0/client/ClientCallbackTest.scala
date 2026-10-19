/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client

import akka.actor.ActorSystem
import akka.dispatch.{
  Dispatcher,
  DispatcherPrerequisites,
  MessageDispatcher,
  MessageDispatcherConfigurator
}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

import java.util.concurrent.{CompletableFuture, RejectedExecutionException}
import java.util.concurrent.TimeUnit.{NANOSECONDS, SECONDS}
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import scala.concurrent.Await
import scala.concurrent.duration._

class ClientCallbackTest {
  @RegisterExtension private val testing =
    AgentInstrumentationExtension.create()

  @RegisterExtension private val cleanup = AutoCleanupExtension.create()

  @Test
  def completesPendingResponseWhenDispatcherRejectsCallbacks(): Unit = {
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
          "akka.actor.default-dispatcher.type = \"" +
            classOf[RejectingDispatcherConfigurator].getName + "\""
        )
        .withFallback(ConfigFactory.load())
    )
    cleanup.deferCleanup(new AutoCloseable {
      override def close(): Unit = Await.result(system.terminate(), 10.seconds)
    })
    val dispatcher = system.dispatcher.asInstanceOf[RejectingDispatcher]
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

    dispatcher.reject = true
    try {
      serverResponse.complete(ServerHttpResponse.of("response body"))
      val result = Await.result(response, 10.seconds)
      assertThat(result.status.intValue()).isEqualTo(200)
      assertThat(
        Await
          .result(result.entity.toStrict(5.seconds), 10.seconds)
          .data
          .utf8String
      ).isEqualTo("response body")
      assertThat(dispatcher.rejections).isGreaterThan(0)

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
      dispatcher.reject = false
    }
  }
}

class RejectingDispatcherConfigurator(
    config: Config,
    prerequisites: DispatcherPrerequisites
) extends MessageDispatcherConfigurator(config, prerequisites) {
  private val instance = new RejectingDispatcher(this)

  override def dispatcher(): MessageDispatcher = instance
}

private[client] class RejectingDispatcher(
    dispatcherConfigurator: MessageDispatcherConfigurator
) extends Dispatcher(
      dispatcherConfigurator,
      dispatcherConfigurator.config.getString("id"),
      dispatcherConfigurator.config.getInt("throughput"),
      Duration(
        dispatcherConfigurator.config
          .getDuration("throughput-deadline-time", NANOSECONDS),
        NANOSECONDS
      ),
      dispatcherConfigurator.configureExecutor(),
      Duration(
        dispatcherConfigurator.config
          .getDuration("shutdown-timeout", NANOSECONDS),
        NANOSECONDS
      )
    ) {
  private val rejectionCount = new AtomicInteger()
  @volatile var reject = false

  def rejections: Int = rejectionCount.get()

  override def execute(runnable: Runnable): Unit = {
    if (reject) {
      rejectionCount.incrementAndGet()
      throw new RejectedExecutionException("test rejection")
    }
    super.execute(runnable)
  }
}
