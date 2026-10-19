/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client

import akka.actor.ActorSystem
import akka.dispatch.{
  Dispatcher,
  DispatcherPrerequisites,
  ExecutionContexts,
  MessageDispatcher,
  MessageDispatcherConfigurator
}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import io.opentelemetry.api.trace.{Span, SpanKind}
import io.opentelemetry.context.{Context, ContextKey}
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable
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
import org.awaitility.Awaitility.await
import org.awaitility.core.{ThrowingRunnable => AwaitilityRunnable}
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

import java.util.concurrent.{
  CompletableFuture,
  ConcurrentLinkedQueue,
  Executor,
  RejectedExecutionException
}
import java.util.concurrent.TimeUnit.{NANOSECONDS, SECONDS}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.function.Consumer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientCallbackTest {
  @RegisterExtension private val testing =
    AgentInstrumentationExtension.create()

  @RegisterExtension private val cleanup = AutoCleanupExtension.create()

  @ParameterizedTest
  @CsvSource(Array("false, false", "false, true", "true, false", "true, true"))
  def completesPendingResponse(reject: Boolean, fail: Boolean): Unit = {
    val requestReceived = new CompletableFuture[String]()
    val serverResponse = new CompletableFuture[ServerHttpResponse]()
    val server = Server
      .builder()
      .http(0)
      .gracefulShutdownTimeoutMillis(0, 0)
      .service(
        "/callback",
        new HttpService {
          override def serve(
              ctx: ServiceRequestContext,
              req: ServerHttpRequest
          ): ServerHttpResponse = {
            requestReceived.complete(req.headers().get("traceparent"))
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
    val callback = new CompletableFuture[Try[HttpResponse]]()
    val completions = new AtomicInteger()
    val callbackContext = new AtomicReference[Context]()
    val key = ContextKey.named[String]("callback-context")
    val previous = Context.current()
    val parentSpan =
      testing.getOpenTelemetry
        .getTracer("test")
        .spanBuilder("parent")
        .startSpan()
    val parent = previous.`with`(parentSpan).`with`(key, "parent")
    try {
      val response = {
        val scope = parent.makeCurrent()
        try {
          val future = Http
            .get(system)
            .singleRequest(
              HttpRequest(
                uri =
                  "http://localhost:" + server.activeLocalPort() + "/callback"
              )
            )
          assertThat(Context.current().get(key)).isEqualTo("parent")
          assertThat(Span.current().getSpanContext)
            .isEqualTo(parentSpan.getSpanContext)
          future.onComplete { result =>
            callbackContext.set(Context.current())
            completions.incrementAndGet()
            testing.runWithSpan(
              "callback",
              new ThrowingRunnable[Exception] {
                override def run(): Unit = ()
              }
            )
            callback.complete(result)
          }(ExecutionContexts.fromExecutor(new Executor {
            override def execute(command: Runnable): Unit = command.run()
          }))
          future
        } finally {
          scope.close()
          parentSpan.end()
        }
      }
      assertThat(Context.current().get(key)).isEqualTo(previous.get(key))
      assertThat(Span.current().getSpanContext)
        .isEqualTo(Span.fromContext(previous).getSpanContext)
      val traceparent = requestReceived.get(10, SECONDS)
      assertThat(response.isCompleted).isFalse()

      dispatcher.reject = reject
      if (fail) {
        server.stop().join()
      } else {
        serverResponse.complete(ServerHttpResponse.of("response body"))
      }
      val result = callback.get(10, SECONDS)
      testing.waitAndAssertTraces(new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit = {
          trace.hasSpansSatisfyingExactly(
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent()
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                span
                  .hasName("GET")
                  .hasKind(SpanKind.CLIENT)
                  .hasParent(trace.getSpan(0))
                if (fail) {
                  span
                    .hasStatus(StatusData.error())
                    .hasException(result.failed.get)
                } else {
                  span
                    .hasStatus(StatusData.unset())
                    .hasEventsSatisfyingExactly()
                }
              }
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("callback")
                  .hasKind(SpanKind.INTERNAL)
                  .hasParent(trace.getSpan(0))
            }
          )
          assertThat(traceparent).isEqualTo(
            "00-" + trace.getSpan(1).getTraceId + "-" +
              trace.getSpan(1).getSpanId + "-" +
              trace.getSpan(1).getSpanContext.getTraceFlags.asHex()
          )
        }
      })
      if (reject) {
        await().untilAsserted(new AwaitilityRunnable {
          override def run(): Unit =
            assertThat(dispatcher.failures).hasSizeGreaterThanOrEqualTo(2)
        })
      } else {
        assertThat(dispatcher.failures).isEmpty()
      }
      dispatcher.reject = false

      assertThat(Await.ready(response, 10.seconds)).isSameAs(response)
      assertThat(response.value.get).isEqualTo(result)
      if (fail) {
        assertThat(result.failed.get)
          .isNotInstanceOf(classOf[RejectedExecutionException])
        assertThat(response.value.get.failed.get).isSameAs(result.failed.get)
      } else {
        val value = Await.result(response, 10.seconds)
        assertThat(result.get).isSameAs(value)
        assertThat(value.status.intValue()).isEqualTo(200)
        assertThat(
          Await
            .result(value.entity.toStrict(5.seconds), 10.seconds)
            .data
            .utf8String
        )
          .isEqualTo("response body")
      }
      assertThat(callbackContext.get().get(key)).isEqualTo("parent")
      assertThat(Span.fromContext(callbackContext.get()).getSpanContext)
        .isEqualTo(parentSpan.getSpanContext)
      assertThat(completions.get()).isEqualTo(1)
      assertThat(Context.current().get(key)).isEqualTo(previous.get(key))
      assertThat(Span.current().getSpanContext)
        .isEqualTo(Span.fromContext(previous).getSpanContext)
      assertThat(dispatcher.failures).allSatisfy(new Consumer[Throwable] {
        override def accept(error: Throwable): Unit =
          assertThat(error).isInstanceOf(classOf[RejectedExecutionException])
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
  private val reportedFailures = new ConcurrentLinkedQueue[Throwable]()
  @volatile var reject = false

  def failures: Array[Throwable] =
    reportedFailures.toArray(new Array[Throwable](0))

  override def execute(runnable: Runnable): Unit = {
    // Reject execution-context callbacks without stopping actor mailboxes.
    if (reject) {
      throw new RejectedExecutionException("dispatcher queue full")
    }
    super.execute(runnable)
  }

  override def reportFailure(cause: Throwable): Unit = {
    reportedFailures.add(cause)
    super.reportFailure(cause)
  }
}
