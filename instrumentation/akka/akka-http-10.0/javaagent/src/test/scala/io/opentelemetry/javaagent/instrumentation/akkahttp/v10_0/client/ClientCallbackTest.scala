/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, IllegalUriException}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.{Span, SpanKind}
import io.opentelemetry.context.{Context, ContextKey}
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.sdk.trace.data.StatusData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{Test, TestInstance}
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import java.util.concurrent.{
  CountDownLatch,
  RejectedExecutionException,
  TimeUnit
}
import java.util.function.Consumer
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration._

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientCallbackTest {
  @RegisterExtension private val testing: AgentInstrumentationExtension =
    AgentInstrumentationExtension.create()

  @ParameterizedTest
  @ValueSource(booleans = Array(true, false))
  def completedResponseCallbacks(withParent: Boolean): Unit = {
    val system = ActorSystem("completed-client")
    try {
      implicit val materializer: ActorMaterializer =
        ActorMaterializer.create(system)
      val marker = ContextKey.named[String]("callback-caller")
      implicit val executionContext: ExecutionContext = new ExecutionContext {
        override def execute(command: Runnable): Unit = {
          val scope = Context.root().makeCurrent()
          try command.run()
          finally scope.close()
        }
        override def reportFailure(error: Throwable): Unit = throw error
      }
      val parent =
        if (withParent)
          testing.getOpenTelemetry
            .getTracer("test")
            .spanBuilder("parent")
            .startSpan()
        else Span.getInvalid
      try {
        val scope =
          Context.root().`with`(parent).`with`(marker, "caller").makeCurrent()
        try {
          val response =
            Http.get(system).singleRequest(HttpRequest(uri = "/relative"))
          Await.ready(response, 5.seconds)
          val callback = Promise[Unit]()
          response.onComplete { _ =>
            assertThat(Span.current().getSpanContext)
              .isEqualTo(parent.getSpanContext)
            assertThat(Context.current().get(marker)).isEqualTo("caller")
            callback.success(())
          }
          Await.result(callback.future, 5.seconds)
          val recovered = response.recover { case _: IllegalUriException =>
            assertThat(Span.current().getSpanContext)
              .isEqualTo(parent.getSpanContext)
            assertThat(Context.current().get(marker)).isEqualTo("caller")
            ()
          }
          Await.result(recovered, 5.seconds)
          // A plain Scala future must retain its normal inline-execution behavior.
          assertThat(
            Await.result(
              Future.successful(1).map(_ => Context.current().get(marker)),
              5.seconds
            )
          ).isNull()
          assertThat(Context.current().get(marker)).isEqualTo("caller")
        } finally {
          scope.close()
        }
      } finally {
        parent.end()
      }
      testing.waitAndAssertTraces(new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit = {
          val client = new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasStatus(StatusData.error())
              if (withParent) span.hasParent(trace.getSpan(0))
              else span.hasNoParent()
            }
          }
          if (withParent)
            trace.hasSpansSatisfyingExactly(
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit =
                  span.hasName("parent").hasNoParent()
              },
              client
            )
          else trace.hasSpansSatisfyingExactly(client)
        }
      })
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  @Test
  def failedResponseWithFullAffinityQueue(): Unit = {
    // The affinity executor is not available in the baseline Akka 2.4.
    assumeTrue(java.lang.Boolean.getBoolean("testLatestDeps"))
    val system = ActorSystem(
      "saturated-client",
      ConfigFactory.parseString("""
        akka.actor.default-dispatcher {
          executor = "affinity-pool-executor"
          affinity-pool-executor {
            parallelism-min = 1
            parallelism-max = 1
            task-queue-size = 64
          }
        }
        akka.loggers-dispatcher = "akka.actor.default-blocking-io-dispatcher"
      """)
    )
    val release = new CountDownLatch(1)
    try {
      implicit val materializer: ActorMaterializer =
        ActorMaterializer.create(system)
      val http = Http.get(system)
      val started = new CountDownLatch(1)
      system.dispatcher.execute(new Runnable {
        override def run(): Unit = {
          started.countDown()
          release.await()
        }
      })
      assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()
      val noop = new Runnable {
        override def run(): Unit = ()
      }
      var rejected = false
      var submitted = 0
      while (!rejected && submitted < 1000) {
        try {
          system.dispatcher.execute(noop)
          submitted += 1
        } catch {
          case _: RejectedExecutionException => rejected = true
        }
      }
      assertThat(rejected).isTrue()

      // Akka fails this request immediately, without any network or dispatcher work.
      val failure = Await.result(
        http.singleRequest(HttpRequest(uri = "/relative")).failed,
        5.seconds
      )
      assertThat(failure).isInstanceOf(classOf[IllegalUriException])
      testing.waitAndAssertTraces(new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasStatus(StatusData.error())
                .hasException(failure)
            }
          })
      })
    } finally {
      release.countDown()
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
