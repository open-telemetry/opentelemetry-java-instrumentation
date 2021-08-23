/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.UnicastProcessor
import reactor.test.StepVerifier
import spock.lang.Specification

class ReactorAsyncOperationEndStrategyTest extends Specification {
  String request = "request"
  String response = "response"
  
  Instrumenter<String, String> instrumenter

  Context context

  Span span

  def underTest = ReactorAsyncOperationEndStrategy.newBuilder()
    .setEmitCheckpoints(true)
    .setTraceMultipleSubscribers(true)
    .build()

  def underTestWithExperimentalAttributes = ReactorAsyncOperationEndStrategy.newBuilder()
    .setEmitCheckpoints(true)
    .setTraceMultipleSubscribers(true)
    .setCaptureExperimentalSpanAttributes(true)
    .build()

  void setup() {
    instrumenter = Mock()
    context = Mock()
    span = Mock()
    span.storeInContext(_) >> { callRealMethod() }
  }

  static class MonoTest extends ReactorAsyncOperationEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Mono)
    }

    def "ends span synchronously on already completed"() {
      when:
      underTest.end(instrumenter, context, request, Mono.just(response), String)

      then:
      1 * instrumenter.end(context, request, response, null)
    }

    def "ends span synchronously on already empty"() {
      when:
      underTest.end(instrumenter, context, request, Mono.empty(), String)

      then:
      1 * instrumenter.end(context, request, null, null)
    }

    def "ends span synchronously on already errored"() {
      given:
      def exception = new IllegalStateException()

      when:
      underTest.end(instrumenter, context, request, Mono.error(exception), String)

      then:
      1 * instrumenter.end(context, request, null, exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()

      when:
      def result = (Mono<?>) underTest.end(instrumenter, context, request, mono, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      source.onNext(response)
      source.onComplete()
      verifier.expectNext(response)
        .verifyComplete()

      then:
      1 * instrumenter.end(context, request, response, null)
    }

    def "ends span when empty"() {
      given:
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()

      when:
      def result = (Mono<?>) underTest.end(instrumenter, context, request, mono, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      source.onComplete()
      verifier.verifyComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()

      when:
      def result = (Mono<?>) underTest.end(instrumenter, context, request, mono, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      source.onError(exception)
      verifier.verifyErrorMatches({ it == exception })

      then:
      1 * instrumenter.end(context, request, null, exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Mono<?>) underTest.end(instrumenter, context, request, mono, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      verifier.thenCancel().verify()

      then:
      1 * instrumenter.end(context, request, null, null)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastProcessor.<String>create()
      def mono = source.singleOrEmpty()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Mono<?>) underTestWithExperimentalAttributes.end(instrumenter, context, request, mono, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      verifier.thenCancel().verify()

      then:
      1 * instrumenter.end(context, request, null, null)
      1 * span.setAttribute({ it.getKey() == "reactor.canceled" }, true)
    }

    def "ends span once for each subscription"() {
      given:
      def mono = Mono.defer({Mono.just(response)})

      when:
      def result = (Mono<?>) underTest.end(instrumenter, context, request, mono, String)
      StepVerifier.create(result)
        .expectNext(response)
        .verifyComplete()
      StepVerifier.create(result)
        .expectNext(response)
        .verifyComplete()
      StepVerifier.create(result)
        .expectNext(response)
        .verifyComplete()

      then:
      3 * instrumenter.end(context, request, response, null)
      2 * instrumenter.shouldStart(_, request) >> true
      2 * instrumenter.start(_, request) >> context
    }
  }

  static class FluxTest extends ReactorAsyncOperationEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Flux)
    }

    def "ends span synchronously on already completed"() {
      when:
      underTest.end(instrumenter, context, request, Flux.just(response), String)

      then:
      1 * instrumenter.end(context, request, response, null)
    }

    def "ends span synchronously on already empty"() {
      when:
      underTest.end(instrumenter, context, request, Flux.empty(), String)

      then:
      1 * instrumenter.end(context, request, null, null)
    }

    def "ends synchronously span on already errored"() {
      given:
      def exception = new IllegalStateException()

      when:
      underTest.end(instrumenter, context, request, Flux.error(exception), String)

      then:
      1 * instrumenter.end(context, request, null, exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastProcessor.<String>create()

      when:
      def result = (Flux<?>) underTest.end(instrumenter, context, request, source, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      source.onNext(response)
      source.onComplete()
      verifier.expectNext(response)
        .verifyComplete()

      then:
      1 * instrumenter.end(context, request, response, null)
    }

    def "ends span when empty"() {
      given:
      def source = UnicastProcessor.<String>create()

      when:
      def result = (Flux<?>) underTest.end(instrumenter, context, request, source, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      source.onComplete()
      verifier.verifyComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastProcessor.<String>create()

      when:
      def result = (Flux<?>) underTest.end(instrumenter, context, request, source, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      source.onError(exception)
      verifier.verifyErrorMatches({ it == exception })

      then:
      1 * instrumenter.end(context, request, null, exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastProcessor.<String>create()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flux<?>) underTest.end(instrumenter, context, request, source, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      verifier.thenCancel()
        .verify()

      then:
      1 * instrumenter.end(context, request, null, null)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastProcessor.<String>create()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flux<?>) underTestWithExperimentalAttributes.end(instrumenter, context, request, source, String)
      def verifier = StepVerifier.create(result)
        .expectSubscription()

      then:
      0 * instrumenter._

      when:
      verifier.thenCancel()
        .verify()

      then:
      1 * instrumenter.end(context, request, null, null)
      1 * span.setAttribute({ it.getKey() == "reactor.canceled" }, true)
    }

    def "ends span once for each subscription"() {
      given:
      def flux = Flux.defer({ Flux.just(response) })

      when:
      def result = (Flux<?>) underTest.end(instrumenter, context, request, flux, String)
      StepVerifier.create(result)
        .expectNext(response)
        .verifyComplete()
      StepVerifier.create(result)
        .expectNext(response)
        .verifyComplete()
      StepVerifier.create(result)
        .expectNext(response)
        .verifyComplete()

      then:
      3 * instrumenter.end(context, request, response, null)
      2 * instrumenter.shouldStart(_, request) >> true
      2 * instrumenter.start(_, request) >> context
    }
  }
}
