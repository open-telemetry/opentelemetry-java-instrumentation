/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.test.annotation.SpanAttributesWithCurrentSpan

import static io.opentelemetry.api.trace.SpanKind.INTERNAL

/**
 * This test verifies that auto instrumentation supports the
 * {@link io.opentelemetry.instrumentation.annotations.WithCurrentSpan}
 * and
 * {@link io.opentelemetry.instrumentation.annotations.SpanAttribute}
 * annotations.
 */
class WithCurrentSpanInstrumentationTest extends AgentInstrumentationSpecification {

  def "should capture attributes in second span"() {
    setup:
    runWithSpan("external",
        { new SpanAttributesWithCurrentSpan().withSpanAttributes("foo", "bar", null, "baz") })

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "external"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "SpanAttributesWithCurrentSpan.withSpanAttributes"
          kind INTERNAL
          childOf span(0)
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" SpanAttributesWithCurrentSpan.name
            "$SemanticAttributes.CODE_FUNCTION" "withSpanAttributes"
            "implicitName" "foo"
            "explicitName" "bar"
          }
        }
      }
    }
  }

  def "should capture only attributes"() {
    setup:
    runWithSpan("external",
        { new SpanAttributesWithCurrentSpan().withCurrentSpanAttributes("foo", "bar", null, "baz") })

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "external"
          kind INTERNAL
          hasNoParent()
          attributes {
            "implicitName" "foo"
            "explicitName" "bar"
          }
        }
      }
    }
  }
}
