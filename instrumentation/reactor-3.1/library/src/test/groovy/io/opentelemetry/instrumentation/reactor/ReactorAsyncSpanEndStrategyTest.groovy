/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

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

  def underTest = ReactorAsyncSpanEndStrategy.INSTANCE

  void setup() {
    tracer = Mock()
    context = Mock()
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
