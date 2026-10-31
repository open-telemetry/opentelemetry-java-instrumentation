/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0

import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import org.junit.jupiter.api.{AfterAll, TestInstance}
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import java.util.function.Consumer
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PekkoTypedActorTest {

  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create

  @AfterAll
  def cleanup(): Unit = {
    PekkoTypedActors.system.terminate()
    Await.result(PekkoTypedActors.system.whenTerminated, 10.seconds)
  }

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def basicTell(count: Int): Unit = {
    val tester = new PekkoTypedActors
    (1 to count).foreach { _ =>
      tester.basicTell()
    }

    assertTraces(count, "Hello, Pekko")
  }

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def basicAsk(count: Int): Unit = {
    val tester = new PekkoTypedActors
    (1 to count).foreach { _ =>
      tester.basicAsk()
    }

    assertTraces(count, "Hello, Pekko")
  }

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def spawnChildAndTell(count: Int): Unit = {
    val tester = new PekkoTypedActors
    (1 to count).foreach { _ =>
      tester.spawnChildAndTell()
    }

    assertTraces(count, "Hi, Pekko")
  }

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def stashAndUnstash(count: Int): Unit = {
    val tester = new PekkoTypedActors
    (1 to count).foreach { _ =>
      tester.stashAndUnstash()
    }

    // the stashed messages are handled while the unlock message is being processed, they should
    // still run in the context that was current when they were sent, the message sent in the root
    // context should not become part of the unlock trace
    // the unlock and Hello, Root spans can share a start timestamp (e.g. millisecond clock on
    // Java 8), so group the traces by root span name instead of relying on start time order
    val assertions =
      ((1 to count).map(_ => traceAssertion("Hello, Pekko")) ++
        (1 to count).map(_ => singleSpanTraceAssertion("unlock")) ++
        (1 to count).map(_ => singleSpanTraceAssertion("Hello, Root"))).asJava

    testing.waitAndAssertSortedTraces(
      TelemetryDataUtil.orderByRootSpanName("parent", "unlock", "Hello, Root"),
      assertions
    )
  }

  private def assertTraces(count: Int, childName: String): Unit = {
    val assertions = (1 to count)
      .map(_ => traceAssertion(childName))
      .asJava

    testing.waitAndAssertTraces(assertions)
  }

  private def singleSpanTraceAssertion(name: String): Consumer[TraceAssert] =
    new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName(name)
                .hasNoParent()
                .hasTotalAttributeCount(0)
            }
          }
        )
    }

  private def traceAssertion(childName: String): Consumer[TraceAssert] =
    new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName("parent")
                .hasTotalAttributeCount(0)
            }
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName(childName)
                .hasParent(trace.getSpan(0))
                .hasTotalAttributeCount(0)
            }
          }
        )
    }
}
