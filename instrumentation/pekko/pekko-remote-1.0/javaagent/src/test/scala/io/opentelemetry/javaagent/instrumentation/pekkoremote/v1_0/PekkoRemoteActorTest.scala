/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0

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
class PekkoRemoteActorTest {

  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create

  @ParameterizedTest
  @ValueSource(ints = Array(1, 150))
  def basicSend(count: Int): Unit = {
    val tester = new PekkoRemoteActors
    (1 to count).foreach { _ =>
      tester.basicForward()
    }

    val assertions = (1 to count)
      .map(_ =>
        new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit = {
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
              },
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span
                    .hasName("Nice to meet you!")
                    .hasParent(trace.getSpan(0))
                    .hasTotalAttributeCount(0)
                }
              }
            )
          }
        }
      )
      .asJava

    testing.waitAndAssertTraces(assertions)
  }

}
