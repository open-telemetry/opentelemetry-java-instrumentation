/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0

import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
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

  private def assertTraces(count: Int, childName: String): Unit = {
    val assertions = (1 to count)
      .map(_ =>
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
      )
      .asJava

    testing.waitAndAssertTraces(assertions)
  }
}
