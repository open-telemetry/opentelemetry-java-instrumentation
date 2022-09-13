/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.test.StepVerifier;

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
    Flux<Integer> source =
        Flux.defer(
            () -> {
              Span.current().setAttribute("inner", "foo");
              return Flux.just(5, 6);
            });
    testing.runWithSpan(
        "parent",
        () ->
            ContextPropagationOperator.ScalarPropagatingFlux.create(source)
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

  @Test
  void monoParentsAccessible() {
    UnicastProcessor<String> source = UnicastProcessor.create();
    Mono<String> mono =
        ContextPropagationOperator.runWithContext(source.singleOrEmpty(), Context.root());

    source.onNext("foo");
    source.onComplete();

    assertThat(mono.block()).isEqualTo("foo");

    assertThat(((Scannable) mono).parents().filter(UnicastProcessor.class::isInstance).findFirst())
        .isPresent();
  }

  @Test
  void fluxParentsAccessible() {
    UnicastProcessor<String> source = UnicastProcessor.create();
    Flux<String> flux = ContextPropagationOperator.runWithContext(source, Context.root());

    source.onNext("foo");
    source.onComplete();

    assertThat(flux.collectList().block()).containsExactly("foo");

    assertThat(((Scannable) flux).parents().filter(UnicastProcessor.class::isInstance).findFirst())
        .isPresent();
  }

  @Test
  void doesNotOverrideInnerCurrentSpans() {
    Flux<Object> publish =
        Flux.create(
            sink -> {
              for (int i = 0; i < 2; i++) {
                Span s = tracer.spanBuilder("inner").startSpan();
                try (Scope scope = s.makeCurrent()) {
                  sink.next(i);
                } finally {
                  s.end();
                }
              }
            });

    // as a result we'll have
    // 1. publish subscriber that creates inner spans
    // 2. tracing subscriber without current context - subscription was done outside any scope
    // 3. inner subscriber that will add onNext attribute to inner spans
    // I.e. tracing subscriber context (root) at subscription time will be different from inner in
    // onNext
    publish
        .take(2)
        .subscribe(
            n -> {
              assertThat(Span.current().getSpanContext().isValid()).isTrue();
              Span.current().setAttribute("onNext", true);
            },
            error -> fail(error.getMessage()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("inner")
                        .hasNoParent()
                        .hasAttributes(attributeEntry("onNext", true))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("inner")
                        .hasNoParent()
                        .hasAttributes(attributeEntry("onNext", true))));
  }

  @Test
  void doesNotOverrideInnerCurrentSpansAsync() {
    Flux<Object> publish =
        Flux.create(
            sink -> {
              Span s = tracer.spanBuilder("inner").startSpan();
              try (Scope scope = s.makeCurrent()) {
                sink.next(s);
              } finally {
                s.end();
              }
            });

    publish
        .take(1)
        .delayElements(Duration.ofMillis(1))
        .doOnNext(
            span -> {
              assertThat(Span.current().getSpanContext().isValid()).isTrue();
              assertThat(Span.current()).isSameAs(span);
            })
        .subscribe(
            span -> assertThat(Span.current()).isSameAs(span), error -> fail(error.getMessage()));

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("inner").hasNoParent()));
  }

  @Test
  void doesNotOverrideInnerCurrentSpansWithThereIsOuterCurrent() {
    Flux<Object> publish =
        Flux.create(
            sink -> {
              for (int i = 0; i < 2; i++) {
                Span s = tracer.spanBuilder("inner").startSpan();
                try (Scope scope = s.makeCurrent()) {
                  sink.next(i);
                } finally {
                  s.end();
                }
              }
            });

    // as a result we'll have
    // 1. publish subscriber that creates inner spans
    // 2. tracing subscriber with outer context - it was active at subscription time
    // 3. inner subscriber that will add onNext attribute
    // I.e. tracing subscriber context at subscription time will be different from inner in onNext
    Span outer = tracer.spanBuilder("outer").startSpan();
    try (Scope scope = outer.makeCurrent()) {
      StepVerifier.create(
              publish
                  .take(2)
                  .doOnNext(
                      n -> {
                        assertThat(Span.current().getSpanContext().isValid()).isTrue();
                        Span.current().setAttribute("onNext", true);
                      })
                  .subscriberContext(
                      // subscribers that know that their subscription can happen
                      // ahead of time and in the 'wrong' context, has to clean up 'wrong' context
                      context ->
                          ContextPropagationOperator.storeOpenTelemetryContext(
                              context, Context.root())))
          .expectNextCount(2)
          .verifyComplete();

      outer.end();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("outer").hasNoParent(),
                span ->
                    span.hasName("inner")
                        .hasParent(trace.getSpan(0))
                        .hasAttributes(attributeEntry("onNext", true)),
                span ->
                    span.hasName("inner")
                        .hasParent(trace.getSpan(0))
                        .hasAttributes(attributeEntry("onNext", true))));
  }

  private <T> Mono<T> monoSpan(Mono<T> mono, String spanName) {
    return ContextPropagationOperator.ScalarPropagatingMono.create(mono)
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
