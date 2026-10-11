/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rx.Observable;
import rx.Subscriber;

@ExtendWith(MockitoExtension.class)
class TracedOnSubscribeTest {
  private static final ContextKey<String> CALLBACK_CONTEXT = ContextKey.named("callback-context");
  private static final ContextKey<String> OPERATION_CONTEXT = ContextKey.named("operation-context");
  private static final ContextKey<String> DERIVED_CONTEXT = ContextKey.named("derived-context");
  private static final Span OPERATION_SPAN =
      Span.wrap(
          SpanContext.create(
              "00000000000000000000000000000001",
              "0000000000000001",
              TraceFlags.getSampled(),
              TraceState.getDefault()));

  @Mock private Instrumenter<String, Void> instrumenter;

  private final Context parentContext = Context.root().with(CALLBACK_CONTEXT, "parent");
  private final Context emitterContext = Context.root().with(CALLBACK_CONTEXT, "emitter");
  private final IllegalStateException error = new IllegalStateException("failure");
  private final AtomicReference<Subscriber<? super String>> sourceSubscriber =
      new AtomicReference<>();
  private final AtomicReference<Context> callbackContext = new AtomicReference<>();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void restoresParentContextForExternallyDrivenError(boolean validSpan) {
    Context operationContext = operationContext(validSpan);
    subscribe(operationContext, recordingSubscriber(operationContext));

    try (Scope ignored = emitterContext.makeCurrent()) {
      sourceSubscriber.get().onError(error);
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    assertThat(callbackContext.get()).isSameAs(parentContext);
    assertThat(callbackContext.get().get(CALLBACK_CONTEXT)).isEqualTo("parent");
    assertThat(callbackContext.get().get(OPERATION_CONTEXT)).isNull();
    verifyNoMoreInteractions(instrumenter);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesOperationContextForSynchronousError(boolean validSpan) {
    Context operationContext = operationContext(validSpan);
    when(instrumenter.start(parentContext, "request")).thenReturn(operationContext);
    TracedOnSubscribe<String, String> traced;
    try (Scope ignored = parentContext.makeCurrent()) {
      traced = new TracedOnSubscribe<>(Observable.error(error), instrumenter, "request");
    }

    try (Scope ignored = emitterContext.makeCurrent()) {
      traced.call(recordingSubscriber(operationContext));
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    assertThat(callbackContext.get()).isSameAs(operationContext);
    verify(instrumenter).start(parentContext, "request");
    verifyNoMoreInteractions(instrumenter);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void selectsContextForErrorFromDerivedOperationContext(boolean validSpan) {
    Context operationContext = operationContext(validSpan);
    Context derivedContext = operationContext.with(DERIVED_CONTEXT, "executor-metadata");
    subscribe(operationContext, recordingSubscriber(operationContext));

    try (Scope ignored = derivedContext.makeCurrent()) {
      sourceSubscriber.get().onError(error);
      assertThat(Context.current()).isSameAs(derivedContext);
    }

    assertThat(callbackContext.get()).isSameAs(validSpan ? operationContext : parentContext);
    assertThat(callbackContext.get().get(DERIVED_CONTEXT)).isNull();
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void restoresParentContextForErrorFromAnotherSpanInSameTrace() {
    Context operationContext = operationContext(true);
    SpanContext operationSpanContext = OPERATION_SPAN.getSpanContext();
    Context unrelatedContext =
        emitterContext.with(
            Span.wrap(
                SpanContext.create(
                    operationSpanContext.getTraceId(),
                    "0000000000000002",
                    operationSpanContext.getTraceFlags(),
                    operationSpanContext.getTraceState())));
    subscribe(operationContext, recordingSubscriber(operationContext));

    try (Scope ignored = unrelatedContext.makeCurrent()) {
      sourceSubscriber.get().onError(error);
      assertThat(Context.current()).isSameAs(unrelatedContext);
    }

    assertThat(callbackContext.get()).isSameAs(parentContext);
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void restoresParentContextForErrorAfterCancellationWithoutEndingTwice() {
    Context operationContext = operationContext(true);
    Subscriber<String> subscriber =
        subscriber(
            e -> {
              assertThat(e).isSameAs(error);
              callbackContext.set(Context.current());
            });
    subscribe(operationContext, subscriber);
    subscriber.unsubscribe();
    subscriber.unsubscribe();

    try (Scope ignored = emitterContext.makeCurrent()) {
      sourceSubscriber.get().onError(error);
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    assertThat(callbackContext.get()).isSameAs(parentContext);
    verify(instrumenter).end(operationContext, "request", null, null);
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void restoresEmitterContextWhenErrorCallbackThrows() {
    Context operationContext = operationContext(true);
    IllegalArgumentException callbackError = new IllegalArgumentException("callback failure");
    subscribe(
        operationContext,
        subscriber(
            e -> {
              assertThat(e).isSameAs(error);
              assertThat(Context.current()).isSameAs(parentContext);
              verify(instrumenter).end(operationContext, "request", null, error);
              throw callbackError;
            }));

    try (Scope ignored = emitterContext.makeCurrent()) {
      assertThatThrownBy(() -> sourceSubscriber.get().onError(error)).isSameAs(callbackError);
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    verifyNoMoreInteractions(instrumenter);
  }

  private Context operationContext(boolean validSpan) {
    return parentContext
        .with(validSpan ? OPERATION_SPAN : Span.getInvalid())
        .with(OPERATION_CONTEXT, "operation");
  }

  private void subscribe(Context operationContext, Subscriber<String> subscriber) {
    when(instrumenter.start(parentContext, "request")).thenReturn(operationContext);
    TracedOnSubscribe<String, String> traced;
    Observable.OnSubscribe<String> source = sourceSubscriber::set;
    try (Scope ignored = parentContext.makeCurrent()) {
      traced = new TracedOnSubscribe<>(Observable.create(source), instrumenter, "request");
    }
    traced.call(subscriber);
    verify(instrumenter).start(parentContext, "request");
  }

  private Subscriber<String> recordingSubscriber(Context operationContext) {
    return subscriber(
        e -> {
          assertThat(e).isSameAs(error);
          callbackContext.set(Context.current());
          verify(instrumenter).end(operationContext, "request", null, error);
        });
  }

  private static Subscriber<String> subscriber(Consumer<Throwable> onError) {
    return new Subscriber<String>() {
      @Override
      public void onNext(String value) {}

      @Override
      public void onCompleted() {}

      @Override
      public void onError(Throwable e) {
        onError.accept(e);
      }
    };
  }
}
