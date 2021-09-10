/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.PRODUCER

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class TracerTest extends AgentInstrumentationSpecification {

  def "test tracer builder"() {
    when:
    def tracer = GlobalOpenTelemetry.get().tracerBuilder("test").build()
    def testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan()
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "test"
          kind PRODUCER
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }
}
