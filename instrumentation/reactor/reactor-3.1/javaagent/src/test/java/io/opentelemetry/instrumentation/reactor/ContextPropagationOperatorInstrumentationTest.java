/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ContextPropagationOperatorInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void storeAndGetContext() {
    reactor.util.context.Context reactorContext = reactor.util.context.Context.empty();
    testing.runWithSpan(
        "parent",
        () -> {
          reactor.util.context.Context newReactorContext =
              ContextPropagationOperator.storeOpenTelemetryContext(
                  reactorContext, Context.current());
          Context otelContext =
              ContextPropagationOperator.getOpenTelemetryContext(newReactorContext, null);
          assertThat(otelContext).isNotNull();
          Span.fromContext(otelContext).setAttribute("foo", "bar");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(attributeEntry("foo", "bar"))));
  }

  @Test
  void missingContext() {
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(
                  ContextPropagationOperator.getOpenTelemetryContext(
                      reactor.util.context.Context.empty(), null))
              .isNull();
          Context otelContext =
              ContextPropagationOperator.getOpenTelemetryContext(
                  reactor.util.context.Context.empty(), Context.current());
          Span.fromContext(otelContext).setAttribute("foo", "bar");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(attributeEntry("foo", "bar"))));
  }

  @Test
  void runMonoWithContextMakesItCurrent() {
    Mono<String> result =
        Mono.defer(
            () -> {
              Span span =
                  testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
              Mono<String> outer = Mono.defer(() -> new TracedWithSpan().mono(Mono.just("Value")));
              return ContextPropagationOperator.runWithContext(outer, Context.current().with(span))
                  .doFinally(unused -> span.end());
            });

    StepVerifier.create(result).expectNext("Value").verifyComplete();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("TracedWithSpan.mono")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void runFluxWithContextMakesItCurrent() {
    Flux<String> result =
        Flux.defer(
            () -> {
              Span span =
                  testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
              Flux<String> outer = Flux.defer(() -> new TracedWithSpan().flux(Flux.just("Value")));
              return ContextPropagationOperator.runWithContext(outer, Context.current().with(span))
                  .doFinally(unused -> span.end());
            });

    StepVerifier.create(result).expectNext("Value").verifyComplete();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("TracedWithSpan.flux")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void storeContextForcesItToBecomeCurrent() {
    Mono<String> result =
        Mono.defer(
            () -> {
              Span span =
                  testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();

              return Mono.delay(Duration.ofMillis(1))
                  .flatMap(
                      unused -> {
                        // usual trick to force this to run under new TracingSubscriber with context
                        // written in the next call
                        return new TracedWithSpan().mono(Mono.just("Value"));
                      })
                  .subscriberContext(
                      ctx ->
                          ContextPropagationOperator.storeOpenTelemetryContext(
                              ctx, Context.current().with(span)))
                  .doFinally(unused -> span.end());
            });

    StepVerifier.create(result).expectNext("Value").verifyComplete();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("TracedWithSpan.mono")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }
}
