/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.tracer.BaseTracer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.UnicastProcessor
import reactor.test.StepVerifier
import spock.lang.Specification

class ReactorAsyncSpanEndStrategyTest extends Specification {
  BaseTracer tracer

  Context context

  Span span

  def underTest = ReactorAsyncSpanEndStrategy.create()

  def underTestWithExperimentalAttributes = ReactorAsyncSpanEndStrategy.newBuilder()
    .setCaptureExperimentalSpanAttributes(true)
    .build()

  void setup() {
    tracer = Mock()
    context = Mock()
    span = Mock()
    span.storeInContext(_) >> { callRealMethod() }
  }

  static class MonoTest extends ReactorAsyncSpanEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Mono)
    }

    def "ends span on already completed"() {
      when:
      def result = (Mono<?>) underTest.end(tracer, context, Mono.just("Value"))
      StepVerifier.create(result)
        .expectNext("Value")
        .verifyComplete()

      then:
      1 * tracer.end(context)
    }

    def "ends span on already empty"() {
      when:
      def result = (Mono<?>) underTest.end(tracer, context, Mono.empty())
      StepVerifier.create(result)
        .verifyComplete()

      then:
      1 * tracer.end(context)
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()

      when:
      def result = (Mono<?>) underTest.end(tracer, context, Mono.error(exception))
      StepVerifier.create(result)
        .verifyErrorMatches({  it == exception })

      then:
      1 * tracer.endExceptionally(context, exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()

      when:
      def result = (Mono<?>) underTest.end(tracer, context, mono)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      source.onNext("Value")
      source.onComplete()
      verifier.expectNext("Value")
        .verifyComplete()

      then:
      1 * tracer.end(context)
    }

    def "ends span when empty"() {
      given:
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()

      when:
      def result = (Mono<?>) underTest.end(tracer, context, mono)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      source.onComplete()
      verifier.verifyComplete()

      then:
      1 * tracer.end(context)
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()

      when:
      def result = (Mono<?>) underTest.end(tracer, context, mono)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      source.onError(exception)
      verifier.verifyErrorMatches({ it == exception })

      then:
      1 * tracer.endExceptionally(context, exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Mono<?>) underTest.end(tracer, context, mono)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      verifier.thenCancel().verify()

      then:
      1 * tracer.end(context)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Mono<?>) underTestWithExperimentalAttributes.end(tracer, context, mono)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      verifier.thenCancel().verify()

      then:
      1 * tracer.end(context)
      1 * span.setAttribute({ it.getKey() == "reactor.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {

      when:
      def result = (Mono<?>) underTest.end(tracer, context, Mono.just("Value"))
      StepVerifier.create(result)
        .expectNext("Value")
        .verifyComplete()
      StepVerifier.create(result)
        .expectNext("Value")
        .verifyComplete()
      StepVerifier.create(result)
        .expectNext("Value")
        .verifyComplete()

      then:
      1 * tracer.end(context)
    }
  }

  static class FluxTest extends ReactorAsyncSpanEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Flux)
    }

    def "ends span on already completed"() {
      when:
      def result = (Flux<?>) underTest.end(tracer, context, Flux.just("Value"))
      StepVerifier.create(result)
        .expectNext("Value")
        .verifyComplete()

      then:
      1 * tracer.end(context)
    }

    def "ends span on already empty"() {
      when:
      def result = (Flux<?>) underTest.end(tracer, context, Flux.empty())
      StepVerifier.create(result)
        .verifyComplete()

      then:
      1 * tracer.end(context)
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()

      when:
      def result = (Flux<?>) underTest.end(tracer, context, Flux.error(exception))
      StepVerifier.create(result)
        .verifyErrorMatches({  it == exception })

      then:
      1 * tracer.endExceptionally(context, exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastProcessor.<String>create()

      when:
      def result = (Flux<?>) underTest.end(tracer, context, source)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      source.onNext("Value")
      source.onComplete()
      verifier.expectNext("Value")
        .verifyComplete()

      then:
      1 * tracer.end(context)
    }

    def "ends span when empty"() {
      given:
      def source = UnicastProcessor.<String>create()

      when:
      def result = (Flux<?>) underTest.end(tracer, context, source)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      source.onComplete()
      verifier.verifyComplete()

      then:
      1 * tracer.end(context)
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastProcessor.<String>create()

      when:
      def result = (Flux<?>) underTest.end(tracer, context, source)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      source.onError(exception)
      verifier.verifyErrorMatches({ it == exception })

      then:
      1 * tracer.endExceptionally(context, exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastProcessor.<String>create()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flux<?>) underTest.end(tracer, context, source)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      verifier.thenCancel()
        .verify()

      then:
      1 * tracer.end(context)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastProcessor.<String>create()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flux<?>) underTestWithExperimentalAttributes.end(tracer, context, source)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * tracer._

      when:
      verifier.thenCancel()
        .verify()

      then:
      1 * tracer.end(context)
      1 * span.setAttribute({ it.getKey() == "reactor.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      when:
      def result = (Flux<?>) underTest.end(tracer, context, Flux.just("Value"))
      StepVerifier.create(result)
        .expectNext("Value")
        .verifyComplete()
      StepVerifier.create(result)
        .expectNext("Value")
        .verifyComplete()
      StepVerifier.create(result)
        .expectNext("Value")
        .verifyComplete()

      then:
      1 * tracer.end(context)
    }
  }
}
