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
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

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

  def "run with context"() {
    setup:
    def result = Mono.defer({ ->
      Span span = GlobalOpenTelemetry.getTracer("test").spanBuilder("parent").startSpan()
      def inner = Mono.defer({ -> new TracedWithSpan().mono(Mono.just("Value") )});
      ContextPropagationOperator
        .runWithContext(inner, Context.current().with(span))
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