/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Operators;

class TracingSubscriberTest {
  private static final ContextKey<String> KEY = ContextKey.named("test");
  private static final Context ACTIVE =
      Context.root()
          .with(KEY, "active")
          .with(
              Span.wrap(
                  SpanContext.create(
                      "22222222222222222222222222222222",
                      "2222222222222222",
                      TraceFlags.getSampled(),
                      TraceState.getDefault())));

  private static Stream<Context> explicitContexts() {
    return Stream.of(
        Context.root(),
        Context.root().with(KEY, "spanless"),
        Context.root().with(Span.getInvalid()).with(KEY, "invalid-span"),
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        "11111111111111111111111111111111",
                        "1111111111111111",
                        TraceFlags.getSampled(),
                        TraceState.getDefault()))));
  }

  @ParameterizedTest
  @MethodSource("explicitContexts")
  void storedContextIsAuthoritative(Context expected) {
    RecordingSubscriber actual = new RecordingSubscriber();
    reactor.util.context.Context reactorContext =
        ContextPropagationOperator.storeOpenTelemetryContext(
            reactor.util.context.Context.of("unrelated", "value"), expected);
    TracingSubscriber<String> subscriber;
    try (Scope ignored = ACTIVE.makeCurrent()) {
      subscriber = new TracingSubscriber<>(actual, reactorContext);
    }
    assertSignals(subscriber, actual, expected);
    assertThat(subscriber.currentContext().get("unrelated").toString()).isEqualTo("value");
  }

  @ParameterizedTest
  @MethodSource("explicitContexts")
  void explicitFallbackIsAuthoritative(Context expected) {
    RecordingSubscriber actual = new RecordingSubscriber();
    assertSignals(
        new TracingSubscriber<>(actual, reactor.util.context.Context.empty(), expected),
        actual,
        expected);
  }

  @Test
  void storedRootOverridesExplicitFallback() {
    RecordingSubscriber actual = new RecordingSubscriber();
    assertSignals(
        new TracingSubscriber<>(
            actual,
            ContextPropagationOperator.storeOpenTelemetryContext(
                reactor.util.context.Context.empty(), Context.root()),
            ACTIVE),
        actual,
        Context.root());
  }

  @Test
  void implicitlyCapturedRootIsTransparent() {
    RecordingSubscriber actual = new RecordingSubscriber();
    TracingSubscriber<String> subscriber;
    try (Scope ignored = Context.root().makeCurrent()) {
      subscriber = new TracingSubscriber<>(actual, reactor.util.context.Context.empty());
    }
    assertSignals(subscriber, actual, ACTIVE);
  }

  @ParameterizedTest
  @MethodSource("explicitContexts")
  void implicitNonRootContextIsPreserved(Context expected) {
    if (expected == Context.root()) {
      return;
    }
    RecordingSubscriber actual = new RecordingSubscriber();
    TracingSubscriber<String> subscriber;
    try (Scope ignored = expected.makeCurrent()) {
      subscriber = new TracingSubscriber<>(actual, reactor.util.context.Context.empty());
    }
    assertSignals(subscriber, actual, expected);
  }

  @Test
  void noCaptureRemovesOverrideAndLeavesSignalsTransparent() {
    reactor.util.context.Context reactorContext =
        ContextPropagationOperator.withoutSubscriptionContextCapture(
            ContextPropagationOperator.storeOpenTelemetryContext(
                reactor.util.context.Context.of("unrelated", "value"), Context.root()));
    assertThat(ContextPropagationOperator.getOpenTelemetryContext(reactorContext, null)).isNull();
    assertThat(reactorContext.get("unrelated").toString()).isEqualTo("value");
    RecordingSubscriber actual = new RecordingSubscriber();
    TracingSubscriber<String> subscriber;
    try (Scope ignored = Context.root().with(KEY, "subscription").makeCurrent()) {
      subscriber = new TracingSubscriber<>(actual, reactorContext);
    }
    assertSignals(subscriber, actual, ACTIVE);
  }

  @Test
  void noCaptureDoesNotRetainImplicitSubscriptionContext() {
    reactor.util.context.Context reactorContext =
        ContextPropagationOperator.withoutSubscriptionContextCapture(
            reactor.util.context.Context.empty());
    RecordingSubscriber actual = new RecordingSubscriber();
    TracingSubscriber<String> subscriber;
    try (Scope ignored = ACTIVE.with(KEY, "subscription").makeCurrent()) {
      subscriber = new TracingSubscriber<>(actual, reactorContext);
    }
    assertThat(subscriber).extracting("traceContext").isSameAs(Context.root());
    assertSignals(subscriber, actual, ACTIVE);
  }

  @ParameterizedTest
  @MethodSource("explicitContexts")
  void storingContextReplacesNoCapture(Context expected) {
    reactor.util.context.Context reactorContext =
        ContextPropagationOperator.storeOpenTelemetryContext(
            ContextPropagationOperator.withoutSubscriptionContextCapture(
                reactor.util.context.Context.empty()),
            expected);
    RecordingSubscriber actual = new RecordingSubscriber();
    assertSignals(new TracingSubscriber<>(actual, reactorContext), actual, expected);
  }

  @Test
  void noCaptureDoesNotDiscardExplicitFallback() {
    RecordingSubscriber actual = new RecordingSubscriber();
    assertSignals(
        new TracingSubscriber<>(
            actual,
            ContextPropagationOperator.withoutSubscriptionContextCapture(
                reactor.util.context.Context.empty()),
            Context.root()),
        actual,
        Context.root());
  }

  @Test
  void restoresContextWhenCallbackThrows() {
    IllegalStateException error = new IllegalStateException("callback");
    RecordingSubscriber actual =
        new RecordingSubscriber() {
          @Override
          public void onNext(String value) {
            assertThat(Context.current()).isSameAs(Context.root());
            throw error;
          }
        };
    TracingSubscriber<String> subscriber =
        new TracingSubscriber<>(actual, reactor.util.context.Context.empty(), Context.root());
    try (Scope ignored = ACTIVE.makeCurrent()) {
      assertThatThrownBy(() -> subscriber.onNext("value")).isSameAs(error);
      assertThat(Context.current()).isSameAs(ACTIVE);
    }
  }

  private static void assertSignals(
      TracingSubscriber<String> subscriber, RecordingSubscriber actual, Context expected) {
    try (Scope ignored = ACTIVE.makeCurrent()) {
      subscriber.onSubscribe(Operators.emptySubscription());
      assertThat(Context.current()).isSameAs(ACTIVE);
      subscriber.onNext("value");
      assertThat(Context.current()).isSameAs(ACTIVE);
      subscriber.onError(new IllegalStateException("test"));
      assertThat(Context.current()).isSameAs(ACTIVE);
      subscriber.onComplete();
      assertThat(Context.current()).isSameAs(ACTIVE);
    }
    assertThat(actual.contexts).containsExactly(expected, expected, expected, expected);
  }

  private static class RecordingSubscriber implements Subscriber<String> {
    private final List<Context> contexts = new ArrayList<>();

    @Override
    public void onSubscribe(Subscription subscription) {
      contexts.add(Context.current());
    }

    @Override
    public void onNext(String value) {
      contexts.add(Context.current());
    }

    @Override
    public void onError(Throwable error) {
      contexts.add(Context.current());
    }

    @Override
    public void onComplete() {
      contexts.add(Context.current());
    }
  }
}
