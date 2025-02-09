/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractWithSpanTest;
import org.junit.jupiter.api.Test;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;
import reactor.test.StepVerifier;

@SuppressWarnings("deprecation") // using deprecated semconv
abstract class BaseFluxWithSpanTest extends AbstractWithSpanTest<Flux<String>, Flux<String>> {

  @Override
  protected void complete(Flux<String> future, String value) {
    UnicastProcessor<String> source = processor(future);
    source.onNext(value);
    source.onComplete();
  }

  @Override
  protected void fail(Flux<String> future, Throwable error) {
    UnicastProcessor<String> source = processor(future);
    source.onError(error);
  }

  @Override
  protected void cancel(Flux<String> future) {
    StepVerifier.create(future).expectSubscription().thenCancel().verify();
  }

  @Override
  protected String getCompleted(Flux<String> future) {
    return future.blockLast();
  }

  @Override
  protected Throwable unwrapError(Throwable t) {
    return t;
  }

  @Override
  protected String canceledKey() {
    return "reactor.canceled";
  }

  @Test
  void nested() {
    Flux<String> flux =
        Flux.defer(
            () -> {
              testing().runWithSpan("inner-manual", () -> {});
              return Flux.just("Value");
            });

    TracedWithSpan traced = newTracedWithSpan();
    Flux<String> result = traced.flux(flux);

    StepVerifier.create(result).expectNext("Value").verifyComplete();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("TracedWithSpan.flux")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(CODE_NAMESPACE, traced.getClass().getName()),
                                equalTo(CODE_FUNCTION, "flux")),
                    span ->
                        span.hasName("inner-manual")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributes(Attributes.empty())));
  }

  @Test
  void nestedFromCurrent() {
    TracedWithSpan traced = newTracedWithSpan();

    testing()
        .runWithSpan(
            "parent",
            () -> {
              Flux<String> result =
                  traced.flux(
                      Flux.defer(
                          () -> {
                            testing().runWithSpan("inner-manual", () -> {});
                            return Flux.just("Value");
                          }));

              StepVerifier.create(result).expectNext("Value").verifyComplete();
            });

    testing()
        .waitAndAssertTraces(
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
                            .hasAttributesSatisfyingExactly(
                                equalTo(CODE_NAMESPACE, traced.getClass().getName()),
                                equalTo(CODE_FUNCTION, "flux")),
                    span ->
                        span.hasName("inner-manual")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))
                            .hasAttributes(Attributes.empty())));
  }

  // While a UnicastProcessor is a Flux and we'd expect a simpler way to access it to provide
  // values,
  // our instrumentation adds operations and causes the return type to always be just a Flux. We
  // need
  // to go through the parents to get back to the processor.
  @SuppressWarnings("unchecked")
  private static UnicastProcessor<String> processor(Flux<String> flux) {
    return ((Scannable) flux)
        .parents()
        .filter(UnicastProcessor.class::isInstance)
        .map(UnicastProcessor.class::cast)
        .findFirst()
        .get();
  }

  abstract TracedWithSpan newTracedWithSpan();
}
