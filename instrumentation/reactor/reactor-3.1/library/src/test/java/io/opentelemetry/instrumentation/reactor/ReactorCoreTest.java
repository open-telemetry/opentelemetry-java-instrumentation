/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ReactorCoreTest extends AbstractReactorCoreTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final ContextPropagationOperator tracingOperator = ContextPropagationOperator.create();
  private final Tracer tracer = testing.getOpenTelemetry().getTracer("test");

  ReactorCoreTest() {
    super(testing);
  }

  @BeforeAll
  void setUp() {
    tracingOperator.registerOnEachOperator();
  }

  @AfterAll
  void tearDown() {
    tracingOperator.resetOnEachOperator();
  }

  @Test
  void monoInNonBlockingPublisherAssembly() {
    testing.runWithSpan(
        "parent",
        () ->
            monoSpan(
                    Mono.fromCallable(
                        () -> {
                          Span.current().setAttribute("inner", "foo");
                          return 1;
                        }),
                    "inner")
                .block());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("inner")
                        .hasParent(trace.getSpan(0))
                        .hasAttributes(attributeEntry("inner", "foo"))));
  }

  @Test
  void fluxInNonBlockingPublisherAssembly() {
    testing.runWithSpan(
        "parent",
        () ->
            ContextPropagationOperator.ScalarPropagatingFlux.INSTANCE
                .flatMap(
                    unused ->
                        Flux.defer(
                            () -> {
                              Span.current().setAttribute("inner", "foo");
                              return Flux.just(5, 6);
                            }))
                .doOnEach(
                    signal -> {
                      if (signal.isOnError()) {
                        // reactor 3.1 does not support getting context here yet
                        Span.current().setStatus(StatusCode.ERROR);
                        Span.current().end();
                      } else if (signal.isOnComplete()) {
                        Span.current().end();
                      }
                    })
                .subscriberContext(
                    ctx -> {
                      Context parent =
                          ContextPropagationOperator.getOpenTelemetryContext(
                              ctx, Context.current());

                      Span innerSpan = tracer.spanBuilder("inner").setParent(parent).startSpan();
                      return ContextPropagationOperator.storeOpenTelemetryContext(
                          ctx, parent.with(innerSpan));
                    })
                .collectList()
                .block());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("inner")
                        .hasParent(trace.getSpan(0))
                        .hasAttributes(attributeEntry("inner", "foo"))));
  }

  @Test
  void nestedNonBlocking() {
    int result =
        testing.runWithSpan(
            "parent",
            () ->
                Mono.defer(
                        () -> {
                          Span.current().setAttribute("middle", "foo");
                          return Mono.fromCallable(
                                  () -> {
                                    Span.current().setAttribute("inner", "bar");
                                    return 1;
                                  })
                              .transform(publisher -> monoSpan(publisher, "inner"));
                        })
                    .transform(publisher -> monoSpan(publisher, "middle"))
                    .block());

    assertThat(result).isEqualTo(1);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("middle")
                        .hasParent(trace.getSpan(0))
                        .hasAttributes(attributeEntry("middle", "foo")),
                span ->
                    span.hasName("inner")
                        .hasParent(trace.getSpan(1))
                        .hasAttributes(attributeEntry("inner", "bar"))));
  }

  @Test
  void noTracingBeforeRegistration() {
    tracingOperator.resetOnEachOperator();

    Integer result1 =
        Mono.fromCallable(
                () -> {
                  assertThat(Span.current().getSpanContext().isValid()).isFalse();
                  return 1;
                })
            .transform(
                mono -> {
                  // NB: Because context propagation is disabled, this span is effectively leaked as
                  // we cannot access it again to
                  // end after processing.
                  Span span = tracer.spanBuilder("before").startSpan();
                  return ContextPropagationOperator.runWithContext(mono, Context.root().with(span))
                      .doOnEach(
                          unused ->
                              assertThat(Span.current().getSpanContext().isValid()).isFalse());
                })
            .block();

    tracingOperator.registerOnEachOperator();
    Integer result2 =
        Mono.fromCallable(
                () -> {
                  assertThat(Span.current().getSpanContext().isValid()).isTrue();
                  return 2;
                })
            .transform(
                mono -> {
                  Span span = tracer.spanBuilder("after").startSpan();
                  return ContextPropagationOperator.runWithContext(mono, Context.root().with(span))
                      .doOnEach(
                          signal -> {
                            assertThat(Span.current().getSpanContext().isValid()).isTrue();
                            if (signal.isOnComplete()) {
                              Span.current().end();
                            }
                          });
                })
            .block();

    assertThat(result1).isEqualTo(1);
    assertThat(result2).isEqualTo(2);

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("after").hasNoParent()));
  }

  private <T> Mono<T> monoSpan(Mono<T> mono, String spanName) {
    return ContextPropagationOperator.ScalarPropagatingMono.INSTANCE
        .flatMap(unused -> mono)
        .doOnEach(
            signal -> {
              if (signal.isOnError()) {
                // reactor 3.1 does not support getting context here yet
                Span.current().setStatus(StatusCode.ERROR);
                Span.current().end();
              } else if (signal.isOnComplete()) {
                Span.current().end();
              }
            })
        .subscriberContext(
            ctx -> {
              Context parent =
                  ContextPropagationOperator.getOpenTelemetryContext(ctx, Context.current());

              Span innerSpan = tracer.spanBuilder(spanName).setParent(parent).startSpan();
              return ContextPropagationOperator.storeOpenTelemetryContext(
                  ctx, parent.with(innerSpan));
            });
  }
}
