/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.reactor.ContextPropagationOperator
import io.opentelemetry.instrumentation.reactor.TracedWithSpan
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

import java.time.Duration

class ContextPropagationOperatorInstrumentationTest extends AgentInstrumentationSpecification {
  def "store and get context"() {

    def reactorContext = reactor.util.context.Context.empty()
    def traceContext = Context.root()
    setup:
    runWithSpan("parent") { ->
      reactorContext = ContextPropagationOperator.storeOpenTelemetryContext(reactorContext, Context.current())
      traceContext = ContextPropagationOperator.getOpenTelemetryContext(reactorContext, null)
      assert traceContext != null
      Span.fromContext(traceContext).setAttribute("foo", "bar")
    }

    expect:
    assert reactorContext.stream().count() == 1
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()

          attributes {
            "foo" "bar"
          }
        }
      }
    }
  }

  def "get missing context"() {
    def traceContext = Context.root()
    setup:
    runWithSpan("parent") { ->
      assert ContextPropagationOperator.getOpenTelemetryContext(reactor.util.context.Context.empty(), null) == null
      traceContext = ContextPropagationOperator.getOpenTelemetryContext(reactor.util.context.Context.empty(), Context.current())
      Span.fromContext(traceContext).setAttribute("foo", "bar")
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()

          attributes {
            "foo" "bar"
          }
        }
      }
    }
  }

  def "run Mono with context forces it to become current"() {
    setup:
    def result = Mono.defer({ ->
      Span span = GlobalOpenTelemetry.getTracer("test").spanBuilder("parent").startSpan()
      def outer = Mono.defer({ -> new TracedWithSpan().mono(Mono.just("Value")) });
      return ContextPropagationOperator
        .runWithContext(outer, Context.current().with(span))
        .doFinally({ i -> span.end() })
    })

    StepVerifier.create(result)
      .expectNext("Value")
      .verifyComplete()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "TracedWithSpan.mono"
          kind SpanKind.INTERNAL
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "run Flux with context forces it to become current"() {
    setup:
    def result = Flux.defer({ ->
      Span span = GlobalOpenTelemetry.getTracer("test").spanBuilder("parent").startSpan()
      def outer = Flux.defer({ -> new TracedWithSpan().flux(Flux.just("Value")) });
      return ContextPropagationOperator
        .runWithContext(outer, Context.current().with(span))
        .doFinally({ i -> span.end() })
    })

    StepVerifier.create(result)
      .expectNext("Value")
      .verifyComplete()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "TracedWithSpan.flux"
          kind SpanKind.INTERNAL
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "store context forces it to become current"() {
    setup:
    def result = Mono.defer({ ->
      Span span = GlobalOpenTelemetry.getTracer("test").spanBuilder("parent").startSpan()

      Mono.delay(Duration.ofMillis(1))
        .flatMap({ t ->
          // usual trick to force this to run under new TracingSubscriber with context written in the next call
          new TracedWithSpan().mono(Mono.just("Value"))
        })
        .subscriberContext({ ctx ->
          ContextPropagationOperator.storeOpenTelemetryContext(ctx, Context.current().with(span))
        })
        .doFinally({ i -> span.end() })
    })

    StepVerifier.create(result)
      .expectNext("Value")
      .verifyComplete()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "TracedWithSpan.mono"
          kind SpanKind.INTERNAL
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }
}