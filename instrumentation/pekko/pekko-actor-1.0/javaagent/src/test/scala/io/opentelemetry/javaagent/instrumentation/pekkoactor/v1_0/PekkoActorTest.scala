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

import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName
import java.util.function.Consumer
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PekkoActorTest {

  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create

  @AfterAll
  def cleanup(): Unit = {
    Await.result(PekkoActors.system.terminate(), 10.seconds)
    Await.result(PekkoActors.startupSystem.terminate(), 10.seconds)
  }

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
                    .hasTotalAttributeCount(0)
                }
              },
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("Howdy, Pekko")
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
                    .hasTotalAttributeCount(0)
                }
              },
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("Howdy, Pekko")
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
                    .hasTotalAttributeCount(0)
                }
              },
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("Hello, Pekko")
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

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def stashAndUnstash(count: Int): Unit = {
    val tester = new PekkoActors
    (1 to count).foreach { _ =>
      tester.stashAndUnstash()
    }

    // the stashed messages are handled after the unlock message, they should still run in the
    // context of the span that was current when they were sent
    val assertions = (1 to count)
      .flatMap(_ =>
        Seq(
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
                      .hasName("Hello, Pekko")
                      .hasParent(trace.getSpan(0))
                      .hasTotalAttributeCount(0)
                  }
                }
              )
          },
          new Consumer[TraceAssert] {
            override def accept(trace: TraceAssert): Unit =
              trace.hasSpansSatisfyingExactly(
                new Consumer[SpanDataAssert] {
                  override def accept(span: SpanDataAssert): Unit = {
                    span
                      .hasName("unlock")
                      .hasTotalAttributeCount(0)
                  }
                }
              )
          }
        )
      )
      .asJava

    testing.waitAndAssertTraces(assertions)
  }

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def tellBeforeStarted(count: Int): Unit = {
    val tester = new PekkoActors
    (1 to count).foreach { _ =>
      tester.tellBeforeStarted()
    }

    // the messages should run in the context they were sent in, not in the context that the actor
    // was created in, the greeting sent without a context is traced after the reply to the ask so
    // the traces are sorted by name instead of by start time
    val createAssertion = new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName("create")
                .hasTotalAttributeCount(0)
            }
          }
        )
    }
    val parentAssertion = new Consumer[TraceAssert] {
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
                .hasName("Hi, Pekko")
                .hasParent(trace.getSpan(0))
                .hasTotalAttributeCount(0)
            }
          }
        )
    }
    val rootAssertion = new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName("Hi, Root")
                .hasNoParent()
                .hasTotalAttributeCount(0)
            }
          }
        )
    }
    val assertions = Seq.fill(count)(createAssertion) ++
      Seq.fill(count)(parentAssertion) ++
      Seq.fill(count)(rootAssertion)

    testing.waitAndAssertSortedTraces(
      orderByRootSpanName("create", "parent", "Hi, Root"),
      assertions.asJava
    )
  }
}
