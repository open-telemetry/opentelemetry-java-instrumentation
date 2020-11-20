/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.ConfigUtils
import io.opentelemetry.test.annotation.TracedWithSpan
import io.opentelemetry.api.trace.Span

/**
 * This test verifies that auto instrumentation supports {@link io.opentelemetry.extension.auto.annotations.WithSpan} contrib annotation.
 */
class WithSpanInstrumentationTest extends AgentTestRunner {
  static final PREVIOUS_CONFIG = ConfigUtils.updateConfigAndResetInstrumentation {
    it.setProperty("", WithSpanInstrumentationTest.name + "*")
    it.setProperty("otel.trace.annotated.methods.exclude", "${TracedWithSpan.name}[ignored]")
  }

  def cleanupSpec() {
    ConfigUtils.setConfig(PREVIOUS_CONFIG)
  }

  def "should derive automatic name"() {
    setup:
    new TracedWithSpan().otel()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.otel"
          kind Span.Kind.INTERNAL
          hasNoParent()
          errored false
          attributes {
            "providerAttr" "Otel"
          }
        }
      }
    }
  }

  def "should take span name from annotation"() {
    setup:
    new TracedWithSpan().namedOtel()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "manualName"
          hasNoParent()
          errored false
          attributes {
            "providerAttr" "Otel"
          }
        }
      }
    }
  }

  def "should take span kind from annotation"() {
    setup:
    new TracedWithSpan().oneOfAKind()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.oneOfAKind"
          kind Span.Kind.PRODUCER
          hasNoParent()
          errored false
          attributes {
            "providerAttr" "Otel"
          }
        }
      }
    }
  }


  def "should ignore method excluded by trace.annotated.methods.exclude configuration"() {
    setup:
    new TracedWithSpan().ignored()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}
  }

}
