/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.concurrent.CompletableFuture

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.SpanKind.SERVER

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.test.annotation.TracedWithSpan

/**
 * This test verifies that auto instrumentation supports {@link io.opentelemetry.extension.annotations.WithSpan} contrib annotation.
 */
class WithSpanInstrumentationTest extends AgentInstrumentationSpecification {

  def "should derive automatic name"() {
    setup:
    new TracedWithSpan().otel()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.otel"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
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
          kind PRODUCER
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture multiple spans"() {
    setup:
    new TracedWithSpan().server()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "TracedWithSpan.server"
          kind SERVER
          hasNoParent()
          errored false
          attributes {
          }
        }
        span(1) {
          name "TracedWithSpan.otel"
          childOf span(0)
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should not capture multiple server spans"() {
    setup:
    new TracedWithSpan().nestedServers()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.nestedServers"
          kind SERVER
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should not capture multiple client spans"() {
    setup:
    new TracedWithSpan().nestedClients()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.nestedClients"
          kind CLIENT
          hasNoParent()
          errored false
          attributes {
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

  def "should capture span for CompletionStage"() {
    setup:
    def future = new CompletableFuture<String>()
    new TracedWithSpan().completionStage(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.complete("Done")
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completionStage"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for CompletionStage on completed exceptionally"() {
    setup:
    def future = new CompletableFuture<String>()
    new TracedWithSpan().completionStage(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.completeExceptionally(new IllegalArgumentException("Boom"))
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completionStage"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored true
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for CompletableFuture"() {
    setup:
    def future = new CompletableFuture<String>()
    new TracedWithSpan().completableFuture(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.complete("Done")
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for CompletableFuture on completed exceptionally"() {
    setup:
    def future = new CompletableFuture<String>()
    new TracedWithSpan().completableFuture(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.completeExceptionally(new IllegalArgumentException("Boom"))
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored true
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }
}
