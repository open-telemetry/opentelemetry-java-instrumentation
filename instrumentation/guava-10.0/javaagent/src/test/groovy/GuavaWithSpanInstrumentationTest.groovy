/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.guava.TracedWithSpan
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class GuavaWithSpanInstrumentationTest extends AgentInstrumentationSpecification {

  def "should capture span for already done ListenableFuture"() {
    setup:
    new TracedWithSpan().listenableFuture(Futures.immediateFuture("Value"))

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.listenableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already failed ListenableFuture"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    new TracedWithSpan().listenableFuture(Futures.immediateFailedFuture(error))

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.listenableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          status StatusCode.ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually done ListenableFuture"() {
    setup:
    def future = SettableFuture.<String> create()
    new TracedWithSpan().listenableFuture(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.set("Value")

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.listenableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually failed ListenableFuture"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def future = SettableFuture.<String> create()
    new TracedWithSpan().listenableFuture(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.setException(error)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.listenableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          status StatusCode.ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for canceled ListenableFuture"() {
    setup:
    def future = SettableFuture.<String> create()
    new TracedWithSpan().listenableFuture(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.cancel(true)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.listenableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
            "guava.canceled" true
          }
        }
      }
    }
  }
}
