/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors

import io.opentelemetry.api.trace.{SpanKind, Tracer}
import io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv
import io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.{
  maybeStable,
  maybeStableDbSystemName
}
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import io.opentelemetry.javaagent.testing.common.Java8BytecodeBridge
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.{Test, TestInstance}
import slick.jdbc.H2Profile.api._

import java.util.function.Consumer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlickTest {

  private val Driver = "h2"
  private val Db = "test"
  private val Username = "TESTUSER"
  private val Url = s"jdbc:${Driver}:mem:${Db}"
  private val TestValue = 3
  private val TestQuery = "SELECT 3"

  private val SleepQuery = "CALL SLEEP(500)"

  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create()

  val tracer: Tracer = testing.getOpenTelemetry().getTracer("test")

  val database = Database.forURL(
    Url,
    user = Username,
    driver = "org.h2.Driver",
    keepAliveConnection = true,
    // Limit number of threads to hit Slick-specific case when we need to avoid re-wrapping
    // wrapped runnables.
    executor = AsyncExecutor("test", numThreads = 1, queueSize = 1000)
  )
  Await.result(
    database.run(
      sqlu"""CREATE ALIAS IF NOT EXISTS SLEEP FOR "java.lang.Thread.sleep(long)""""
    ),
    Duration.Inf
  )

  @Test
  def basicStatement(): Unit = {
    val result = getResults(startQuery(TestQuery))

    assertThat(result).isEqualTo(TestValue)

    testing.waitAndAssertTraces(
      new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("run query")
                  .hasNoParent
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName(s"SELECT ${Db}")
                  .hasKind(SpanKind.CLIENT)
                  .hasParent(trace.getSpan(0))
                  .hasAttributesSatisfyingExactly(
                    equalTo(
                      maybeStable(DB_SYSTEM),
                      maybeStableDbSystemName(DbSystemIncubatingValues.H2)
                    ),
                    equalTo(maybeStable(DB_NAME), Db),
                    equalTo(
                      DB_USER,
                      if (emitStableDatabaseSemconv()) null else Username
                    ),
                    equalTo(
                      DB_CONNECTION_STRING,
                      if (emitStableDatabaseSemconv()) null else "h2:mem:"
                    ),
                    equalTo(maybeStable(DB_STATEMENT), "SELECT ?"),
                    equalTo(maybeStable(DB_OPERATION), "SELECT")
                  )
            }
          )
      }
    )
  }

  @Test
  def concurrentRequests(): Unit = {
    val sleepFuture = startQuery(SleepQuery)
    val result = getResults(startQuery(TestQuery))

    getResults(sleepFuture)

    assertThat(result).isEqualTo(TestValue)

    // Expect two traces in arbitrary order because two queries have been run
    testing.waitAndAssertTraces(
      new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("run query")
                  .hasNoParent()
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  // Don't check details since the order isn't well defined and it isn't too relevant to the test.
                  .hasKind(SpanKind.CLIENT)
                  .hasParent(trace.getSpan(0))
            }
          )
      },
      new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("run query")
                  .hasNoParent()
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  // Don't check details since the order isn't well defined and it isn't too relevant to the test.
                  .hasKind(SpanKind.CLIENT)
                  .hasParent(trace.getSpan(0))
            }
          )
      }
    )
  }

  private def startQuery(query: String): Future[Vector[Int]] = {
    val span = tracer.spanBuilder("run query").startSpan()
    val scope = Java8BytecodeBridge.currentContext().`with`(span).makeCurrent()
    try {
      database.run(sql"#$query".as[Int])
    } finally {
      span.end()
      scope.close()
    }
  }

  private def getResults(future: Future[Vector[Int]]): Int = {
    Await.result(future, Duration.Inf).head
  }
}
