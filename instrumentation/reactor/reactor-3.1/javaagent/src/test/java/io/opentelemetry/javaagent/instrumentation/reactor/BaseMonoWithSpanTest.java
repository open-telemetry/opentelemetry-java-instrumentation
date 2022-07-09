/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.otelannotations.AbstractWithSpanTest;
import org.junit.jupiter.api.Test;
import reactor.core.Scannable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.test.StepVerifier;

abstract class BaseMonoWithSpanTest extends AbstractWithSpanTest<Mono<String>, Mono<String>> {

  @Override
  protected void complete(Mono<String> future, String value) {
    UnicastProcessor<String> source = processor(future);
    source.onNext(value);
    source.onComplete();
  }

  @Override
  protected void fail(Mono<String> future, Throwable error) {
    UnicastProcessor<String> source = processor(future);
    source.onError(error);
  }

  @Override
  protected void cancel(Mono<String> future) {
    StepVerifier.create(future).expectSubscription().thenCancel().verify();
  }

  @Override
  protected String getCompleted(Mono<String> future) {
    return future.block();
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
    Mono<String> mono =
        Mono.defer(
            () -> {
              testing().runWithSpan("inner-manual", () -> {});
              return Mono.just("Value");
            });

    Mono<String> result = newTracedWithSpan().outer(mono);

    StepVerifier.create(result).expectNext("Value").verifyComplete();

    testing()
        .waitAndAssertTraces(
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
    testing()
        .runWithSpan(
            "parent",
            () -> {
              Mono<String> result =
                  newTracedWithSpan()
                      .mono(
                          Mono.defer(
                              () -> {
                                testing().runWithSpan("inner-manual", () -> {});
                                return Mono.just("Value");
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

  // Because we test on the Mono API but need to be able to complete the processor, we
  // use this hacky approach to access the processor from the mono ancestor.
  @SuppressWarnings("unchecked")
  private static UnicastProcessor<String> processor(Mono<String> mono) {
    return ((Scannable) mono)
        .parents()
        .filter(UnicastProcessor.class::isInstance)
        .map(UnicastProcessor.class::cast)
        .findFirst()
        .get();
  }

  abstract TracedWithSpan newTracedWithSpan();
}
