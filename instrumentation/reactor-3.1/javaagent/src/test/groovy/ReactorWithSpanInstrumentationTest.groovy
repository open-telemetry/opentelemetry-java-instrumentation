/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.reactor.TracedWithSpan
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.ReplayProcessor
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
    def source = UnicastProcessor.<String>create()
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

  def "should capture span for eventually completed Mono per subscription"() {
    setup:
    def source = ReplayProcessor.<String>create()
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

    StepVerifier.create(result)
      .expectNext("Value")
      .verifyComplete()

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.mono"
          kind SpanKind.INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
      trace(1, 1) {
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
    def source = UnicastProcessor.<String>create()
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
    def source = UnicastProcessor.<String>create()
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
    def source = UnicastProcessor.<String>create()
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
    def source = UnicastProcessor.<String>create()
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
    def source = UnicastProcessor.<String>create()
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
