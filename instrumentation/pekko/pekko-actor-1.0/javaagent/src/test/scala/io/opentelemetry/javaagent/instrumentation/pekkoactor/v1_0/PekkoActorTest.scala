/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import java.util.function.Consumer
import scala.collection.JavaConverters._

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PekkoActorTest {

  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def basicTell(count: Int): Unit = {
    val tester = new PekkoActors
    (1 to count).foreach { _ =>
      tester.basicTell()
    }

    val assertions = (1 to count)
      .map(_ =>
        new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("parent")
                    .hasAttributes(Attributes.empty())
                }
              },
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("Howdy, Pekko")
                    .hasParent(trace.getSpan(0))
                    .hasAttributes(Attributes.empty())
                }
              }
            )
        }
      )
      .asJava

    testing.waitAndAssertTraces(assertions)
  }

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def basicAsk(count: Int): Unit = {
    val tester = new PekkoActors
    (1 to count).foreach { _ =>
      tester.basicAsk()
    }

    val assertions = (1 to count)
      .map(_ =>
        new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("parent")
                    .hasAttributes(Attributes.empty())
                }
              },
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("Howdy, Pekko")
                    .hasParent(trace.getSpan(0))
                    .hasAttributes(Attributes.empty())
                }
              }
            )
        }
      )
      .asJava

    testing.waitAndAssertTraces(assertions)
  }

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def basicForward(count: Int): Unit = {
    val tester = new PekkoActors
    (1 to count).foreach { _ =>
      tester.basicForward()
    }

    val assertions = (1 to count)
      .map(_ =>
        new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("parent")
                    .hasAttributes(Attributes.empty())
                }
              },
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("Hello, Pekko")
                    .hasParent(trace.getSpan(0))
                    .hasAttributes(Attributes.empty())
                }
              }
            )
        }
      )
      .asJava

    testing.waitAndAssertTraces(assertions)
  }
}
