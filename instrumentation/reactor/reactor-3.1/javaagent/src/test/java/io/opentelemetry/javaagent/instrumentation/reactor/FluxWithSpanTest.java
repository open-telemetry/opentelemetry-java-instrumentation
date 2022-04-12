/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractTraced;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractWithSpanTest;
import org.junit.jupiter.api.Test;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;
import reactor.test.StepVerifier;

class FluxWithSpanTest extends AbstractWithSpanTest<Flux<String>, Flux<String>> {

  @Override
  protected AbstractTraced<Flux<String>, Flux<String>> newTraced() {
    return new Traced();
  }

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

    Flux<String> result = new TracedWithSpan().flux(flux);

    StepVerifier.create(result).expectNext("Value").verifyComplete();

    testing()
        .waitAndAssertTraces(
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
    testing()
        .runWithSpan(
            "parent",
            () -> {
              Flux<String> result =
                  new TracedWithSpan()
                      .flux(
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
                            .hasAttributes(Attributes.empty()),
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

  static class Traced extends AbstractTraced<Flux<String>, Flux<String>> {

    @Override
    @WithSpan
    protected Flux<String> completable() {
      return UnicastProcessor.create();
    }

    @Override
    @WithSpan
    protected Flux<String> alreadySucceeded() {
      return Flux.just(SUCCESS_VALUE);
    }

    @Override
    @WithSpan
    protected Flux<String> alreadyFailed() {
      return Flux.error(FAILURE);
    }
  }
}
