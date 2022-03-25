/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.test.StepVerifier;

@SuppressWarnings("ClassCanBeStatic")
class ReactorWithSpanInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Nested
  class MonoTest {
    @Test
    void success() {
      UnicastProcessor<String> source = UnicastProcessor.create();
      Mono<String> mono = source.singleOrEmpty();
      Mono<String> result = new TracedWithSpan().mono(mono);
      StepVerifier.Step<String> verifier = StepVerifier.create(result).expectSubscription();

      source.onNext("Value");
      source.onComplete();
      verifier.expectNext("Value").verifyComplete();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.mono")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void failure() {
      IllegalArgumentException error = new IllegalArgumentException("Boom");

      UnicastProcessor<String> source = UnicastProcessor.create();
      Mono<String> mono = source.singleOrEmpty();
      Mono<String> result = new TracedWithSpan().mono(mono);
      StepVerifier.Step<String> verifier = StepVerifier.create(result).expectSubscription();

      source.onError(error);
      verifier.verifyErrorMatches(t -> t.equals(error));

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.mono")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasException(error)
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void canceled() {
      UnicastProcessor<String> source = UnicastProcessor.create();
      Mono<String> mono = source.singleOrEmpty();
      Mono<String> result = new TracedWithSpan().mono(mono);
      StepVerifier.Step<String> verifier = StepVerifier.create(result).expectSubscription();

      verifier.thenCancel().verify();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.mono")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(attributeEntry("reactor.canceled", true))));
    }

    @Test
    void immediateSuccess() {
      Mono<String> result = new TracedWithSpan().mono(Mono.just("Value"));
      StepVerifier.create(result).expectNext("Value").verifyComplete();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.mono")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void immediateFailure() {
      IllegalArgumentException error = new IllegalArgumentException("Boom");
      Mono<String> result = new TracedWithSpan().mono(Mono.error(error));
      StepVerifier.create(result).verifyErrorMatches(t -> t.equals(error));

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.mono")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasException(error)
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void nested() {
      Mono<String> mono =
          Mono.defer(
              () -> {
                testing.runWithSpan("inner-manual", () -> {});
                return Mono.just("Value");
              });

      Mono<String> result = new TracedWithSpan().outer(mono);

      StepVerifier.create(result).expectNext("Value").verifyComplete();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.outer")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(Attributes.empty()),
                  span ->
                      span.hasName("TracedWithSpan.mono")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))
                          .hasAttributes(Attributes.empty()),
                  span ->
                      span.hasName("inner-manual")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(1))
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void nestedFromCurrent() {
      testing.runWithSpan(
          "parent",
          () -> {
            Mono<String> result =
                new TracedWithSpan()
                    .mono(
                        Mono.defer(
                            () -> {
                              testing.runWithSpan("inner-manual", () -> {});
                              return Mono.just("Value");
                            }));

            StepVerifier.create(result).expectNext("Value").verifyComplete();
          });

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("parent")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(Attributes.empty()),
                  span ->
                      span.hasName("TracedWithSpan.mono")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))
                          .hasAttributes(Attributes.empty()),
                  span ->
                      span.hasName("inner-manual")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(1))
                          .hasAttributes(Attributes.empty())));
    }
  }

  @Nested
  class FluxTest {
    @Test
    void success() {
      UnicastProcessor<String> source = UnicastProcessor.create();
      Flux<String> result = new TracedWithSpan().flux(source);
      StepVerifier.Step<String> verifier = StepVerifier.create(result).expectSubscription();

      source.onNext("Value");
      source.onComplete();
      verifier.expectNext("Value").verifyComplete();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.flux")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void failure() {
      IllegalArgumentException error = new IllegalArgumentException("Boom");

      UnicastProcessor<String> source = UnicastProcessor.create();
      Flux<String> result = new TracedWithSpan().flux(source);
      StepVerifier.Step<String> verifier = StepVerifier.create(result).expectSubscription();

      source.onError(error);
      verifier.verifyErrorMatches(t -> t.equals(error));

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.flux")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasException(error)
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void canceled() {
      UnicastProcessor<String> source = UnicastProcessor.create();
      Flux<String> result = new TracedWithSpan().flux(source);
      StepVerifier.Step<String> verifier = StepVerifier.create(result).expectSubscription();

      verifier.thenCancel().verify();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.flux")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(attributeEntry("reactor.canceled", true))));
    }

    @Test
    void immediateSuccess() {
      Flux<String> result = new TracedWithSpan().flux(Flux.just("Value"));
      StepVerifier.create(result).expectNext("Value").verifyComplete();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.flux")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void immediateFailure() {
      IllegalArgumentException error = new IllegalArgumentException("Boom");
      Flux<String> result = new TracedWithSpan().flux(Flux.error(error));
      StepVerifier.create(result).verifyErrorMatches(t -> t.equals(error));

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.flux")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasException(error)
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void nested() {
      Flux<String> flux =
          Flux.defer(
              () -> {
                testing.runWithSpan("inner-manual", () -> {});
                return Flux.just("Value");
              });

      Flux<String> result = new TracedWithSpan().flux(flux);

      StepVerifier.create(result).expectNext("Value").verifyComplete();

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TracedWithSpan.flux")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(Attributes.empty()),
                  span ->
                      span.hasName("inner-manual")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))
                          .hasAttributes(Attributes.empty())));
    }

    @Test
    void nestedFromCurrent() {
      testing.runWithSpan(
          "parent",
          () -> {
            Flux<String> result =
                new TracedWithSpan()
                    .flux(
                        Flux.defer(
                            () -> {
                              testing.runWithSpan("inner-manual", () -> {});
                              return Flux.just("Value");
                            }));

            StepVerifier.create(result).expectNext("Value").verifyComplete();
          });

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("parent")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributes(Attributes.empty()),
                  span ->
                      span.hasName("TracedWithSpan.flux")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))
                          .hasAttributes(Attributes.empty()),
                  span ->
                      span.hasName("inner-manual")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(1))
                          .hasAttributes(Attributes.empty())));
    }
  }
}
