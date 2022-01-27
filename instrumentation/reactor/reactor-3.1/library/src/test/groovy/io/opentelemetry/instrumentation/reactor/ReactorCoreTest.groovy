/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Shared

class ReactorCoreTest extends AbstractReactorCoreTest implements LibraryTestTrait {
  @Shared
  ContextPropagationOperator tracingOperator = ContextPropagationOperator.create()

  def setupSpec() {
    tracingOperator.registerOnEachOperator()
  }

  def cleanupSpec() {
    tracingOperator.resetOnEachOperator()
  }

  def "Current in non-blocking publisher assembly"() {
    when:
    runWithSpan({
      return publisherSupplier().transform({ publisher -> traceNonBlocking(publisher, "inner") })
    })

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "trace-parent"
          hasNoParent()
          attributes {
          }
        }

        span(1) {
          name "publisher-parent"
          kind SpanKind.INTERNAL
          childOf span(0)
        }

        span(2) {
          name "inner"
          childOf span(1)
          attributes {
            "inner" "foo"
          }
        }
      }
    }

    where:
    paramName    | publisherSupplier
    "basic mono" | { ->
      Mono.fromCallable({ i ->
        Span.current().setAttribute("inner", "foo")
        return 1
      })
    }
    "basic flux" | { ->
      Flux.defer({
        Span.current().setAttribute("inner", "foo")
        return Flux.just([5, 6].toArray())
      })
    }
  }

  def "Nested non-blocking"() {
    when:
    def result = runWithSpan({
      Mono.defer({ ->
        Span.current().setAttribute("middle", "foo")
        return Mono.fromCallable({ ->
          Span.current().setAttribute("inner", "bar")
          return 1
        })
          .transform({ i -> traceNonBlocking(i, "inner") })
      })
        .transform({ m -> traceNonBlocking(m, "middle") })
    })

    then:
    result == 1
    and:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "trace-parent"
          hasNoParent()
          attributes {
          }
        }

        span(1) {
          name "publisher-parent"
          kind SpanKind.INTERNAL
          childOf span(0)
        }

        span(2) {
          name "middle"
          childOf span(1)
          attributes {
            "middle" "foo"
          }
        }

        span(3) {
          name "inner"
          childOf span(2)
          attributes {
            "inner" "bar"
          }
        }
      }
    }
  }


  def "No tracing before registration"() {
    when:
    tracingOperator.resetOnEachOperator()

    def result1 = Mono.fromCallable({ ->
      assert !Span.current().getSpanContext().isValid(): "current span is not set"
      return 1
    })
      .transform({ i ->

        def beforeSpan = GlobalOpenTelemetry.getTracer("test").spanBuilder("before").startSpan()

        return ContextPropagationOperator
          .runWithContext(i, Context.root().with(beforeSpan))
          .doOnEach({ signal ->
            assert !Span.current().getSpanContext().isValid(): "current span is not set"
          })
      }).block()

    tracingOperator.registerOnEachOperator()
    def result2 = Mono.fromCallable({ ->
      assert Span.current().getSpanContext().isValid(): "current span is set"
      return 2
    })
      .transform({ i ->

        def afterSpan = GlobalOpenTelemetry.getTracer("test").spanBuilder("after").startSpan()

        return ContextPropagationOperator
          .runWithContext(i, Context.root().with(afterSpan))
          .doOnEach({ signal ->
            assert Span.current().getSpanContext().isValid(): "current span is set"
            if (signal.isOnComplete()) {
              Span.current().end()
            }
          })
      }).block()

    then:
    result1 == 1
    result2 == 2
    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "after"
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def traceNonBlocking(def publisher, def spanName) {
    return getDummy(publisher)
      .flatMap({ i -> publisher })
      .doOnEach({ signal ->
        if (signal.isOnError()) {
          // reactor 3.1 does not support getting context here yet
          Span.current().setStatus(StatusCode.ERROR)
          Span.current().end()
        } else if (signal.isOnComplete()) {
          Span.current().end()
        }
      })
      .subscriberContext({ ctx ->

        def parent = ContextPropagationOperator.getOpenTelemetryContext(ctx, Context.current())

        def innerSpan = GlobalOpenTelemetry.getTracer("test")
          .spanBuilder(spanName)
          .setParent(parent)
          .startSpan()

        return ContextPropagationOperator.storeOpenTelemetryContext(ctx, parent.with(innerSpan))
      })
  }

  def getDummy(def publisher) {
    if (publisher instanceof Mono) {
      return ContextPropagationOperator.ScalarPropagatingMono.INSTANCE
    } else if (publisher instanceof Flux) {
      return ContextPropagationOperator.ScalarPropagatingFlux.INSTANCE
    }

    throw new IllegalStateException("Unknown publisher")
  }
}
