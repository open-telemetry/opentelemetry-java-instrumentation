/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Scope
import io.opentelemetry.instrumentation.reactor.TracedWithSpan
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.UnicastProcessor
import reactor.test.StepVerifier

class ReactorWithSpanInstrumentationTest extends AgentInstrumentationSpecification {

  def "should capture span for already completed Mono"() {
    setup:
    def source = Mono.just("Value")
    def result = new TracedWithSpan()
      .mono(source)

    expect:
    StepVerifier.create(result)
      .expectNext("Value")
      .verifyComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.mono"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed Mono"() {
    setup:
    def source = UnicastProcessor.<String> create()
    def mono = source.singleOrEmpty()
    def result = new TracedWithSpan()
      .mono(mono)
    def verifier = StepVerifier.create(result)
      .expectSubscription()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    source.onComplete()

    verifier.expectNext("Value")
      .verifyComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.mono"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture nested Mono spans"() {
    setup:
    def mono = Mono.defer({ ->
      Span span = GlobalOpenTelemetry.getTracer("test").spanBuilder("inner-manual").startSpan()
      span.end()
      return Mono.just("Value")
    })

    def result = new TracedWithSpan()
      .outer(mono)

    StepVerifier.create(result)
      .expectNext("Value")
      .verifyComplete()

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "TracedWithSpan.outer"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "TracedWithSpan.mono"
          kind SpanKind.INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          name "inner-manual"
          kind SpanKind.INTERNAL
          childOf span(1)
          attributes {
          }
        }
      }
    }
  }

  def "should capture nested spans from current"() {
    setup:
    Span parent = GlobalOpenTelemetry.getTracer("test")
      .spanBuilder("parent").startSpan()

    Scope scope = parent.makeCurrent()

    def result = new TracedWithSpan()
      .mono(Mono.defer({ ->
        Span inner = GlobalOpenTelemetry.getTracer("test").spanBuilder("inner-manual").startSpan()
        inner.end()
        return Mono.just("Value")
      }))

    StepVerifier.create(result)
      .expectNext("Value")
      .verifyComplete()

    scope.close()
    parent.end()

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "TracedWithSpan.mono"
          kind SpanKind.INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          name "inner-manual"
          kind SpanKind.INTERNAL
          childOf span(1)
          attributes {
          }
        }
      }
    }
  }

  def "should capture nested Flux spans"() {
    setup:
    def mono = Flux.defer({ ->
      Span span = GlobalOpenTelemetry.getTracer("test").spanBuilder("inner-manual").startSpan()
      span.end()
      return Flux.just("Value")
    })

    def result = new TracedWithSpan()
      .flux(mono)

    StepVerifier.create(result)
      .expectNext("Value")
      .verifyComplete()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "TracedWithSpan.flux"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "inner-manual"
          kind SpanKind.INTERNAL
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already errored Mono"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = Mono.error(error)
    def result = new TracedWithSpan()
      .mono(source)

    expect:
    StepVerifier.create(result)
      .verifyErrorMatches({ it == error })

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.mono"
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

  def "should capture span for eventually errored Mono"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = UnicastProcessor.<String> create()
    def mono = source.singleOrEmpty()
    def result = new TracedWithSpan()
      .mono(mono)
    def verifier = StepVerifier.create(result)
      .expectSubscription()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)

    verifier
      .verifyErrorMatches({ it == error })

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.mono"
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

  def "should capture span for canceled Mono"() {
    setup:
    def source = UnicastProcessor.<String> create()
    def mono = source.singleOrEmpty()
    def result = new TracedWithSpan()
      .mono(mono)
    def verifier = StepVerifier.create(result)
      .expectSubscription()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    verifier.thenCancel().verify()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.mono"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
            "reactor.canceled" true
          }
        }
      }
    }
  }

  def "should capture span for already completed Flux"() {
    setup:
    def source = Flux.just("Value")
    def result = new TracedWithSpan()
      .flux(source)

    expect:
    StepVerifier.create(result)
      .expectNext("Value")
      .verifyComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flux"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed Flux"() {
    setup:
    def source = UnicastProcessor.<String> create()
    def result = new TracedWithSpan()
      .flux(source)
    def verifier = StepVerifier.create(result)
      .expectSubscription()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    source.onComplete()

    verifier.expectNext("Value")
      .verifyComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flux"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already errored Flux"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = Flux.error(error)
    def result = new TracedWithSpan()
      .flux(source)

    expect:
    StepVerifier.create(result)
      .verifyErrorMatches({ it == error })

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flux"
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

  def "should capture span for eventually errored Flux"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = UnicastProcessor.<String> create()
    def result = new TracedWithSpan()
      .flux(source)
    def verifier = StepVerifier.create(result)
      .expectSubscription()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)

    verifier.verifyErrorMatches({ it == error })

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flux"
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

  def "should capture span for canceled Flux"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = UnicastProcessor.<String> create()
    def result = new TracedWithSpan()
      .flux(source)
    def verifier = StepVerifier.create(result)
      .expectSubscription()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)

    verifier.thenCancel().verify()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flux"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
            "reactor.canceled" true
          }
        }
      }
    }
  }
}
