/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rx.Observable;
import rx.Subscriber;

@ExtendWith(MockitoExtension.class)
class TracedOnSubscribeTest {

  private static final ContextKey<String> CALLBACK_CONTEXT =
      ContextKey.named("test-callback-context");
  private static final ContextKey<String> OPERATION_CONTEXT =
      ContextKey.named("test-operation-context");

  @Mock private Instrumenter<TestRequest, Void> instrumenter;

  @Test
  void restoresOperationContextForExternallyDrivenSuccess() {
    TestSource source = new TestSource();
    TestRequest request = new TestRequest("request");
    Context parentContext = Context.root().with(CALLBACK_CONTEXT, "parent");
    Context operationContext = parentContext.with(OPERATION_CONTEXT, "operation");
    Context emitterContext =
        Context.root()
            .with(CALLBACK_CONTEXT, "emitter")
            .with(OPERATION_CONTEXT, "emitter-operation");
    when(instrumenter.start(parentContext, request)).thenReturn(operationContext);

    TracedOnSubscribe<String, TestRequest> traced =
        tracedOnSubscribe(source, request, parentContext);
    RecordingSubscriber subscriber = new RecordingSubscriber();
    traced.call(subscriber);

    try (Scope ignored = emitterContext.makeCurrent()) {
      source.subscribers.get(0).onNext("value");
      assertThat(Context.current()).isSameAs(emitterContext);
      source.subscribers.get(0).onCompleted();
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    assertThat(Span.fromContext(parentContext).getSpanContext().isValid()).isFalse();
    assertThat(subscriber.values).containsExactly("value");
    assertThat(subscriber.onNextContext).isSameAs(operationContext);
    assertThat(subscriber.onCompletedContext).isSameAs(operationContext);
    assertThat(subscriber.completionCount).isEqualTo(1);
    verify(instrumenter).start(parentContext, request);
    verify(instrumenter).end(operationContext, request, null, null);
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void restoresParentContextForExternallyDrivenError() {
    TestSource source = new TestSource();
    TestRequest request = new TestRequest("request");
    IllegalStateException error = new IllegalStateException("failure");
    Context parentContext = Context.root().with(CALLBACK_CONTEXT, "parent");
    Context operationContext = parentContext.with(OPERATION_CONTEXT, "operation");
    Context emitterContext =
        Context.root()
            .with(CALLBACK_CONTEXT, "emitter")
            .with(OPERATION_CONTEXT, "emitter-operation");
    when(instrumenter.start(parentContext, request)).thenReturn(operationContext);

    TracedOnSubscribe<String, TestRequest> traced =
        tracedOnSubscribe(source, request, parentContext);
    RecordingSubscriber subscriber = new RecordingSubscriber();
    traced.call(subscriber);

    try (Scope ignored = emitterContext.makeCurrent()) {
      source.subscribers.get(0).onError(error);
      assertThat(Context.current()).isSameAs(emitterContext);
    }

    assertThat(Span.fromContext(parentContext).getSpanContext().isValid()).isFalse();
    assertThat(subscriber.error).isSameAs(error);
    assertThat(subscriber.onErrorContext).isSameAs(parentContext);
    assertThat(subscriber.onErrorContext.get(CALLBACK_CONTEXT)).isEqualTo("parent");
    assertThat(subscriber.onErrorContext.get(OPERATION_CONTEXT)).isNull();
    verify(instrumenter).start(parentContext, request);
    verify(instrumenter).end(operationContext, request, null, error);
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void endsOnceWhenCancelledBeforeCompletion() {
    TestSource source = new TestSource();
    TestRequest request = new TestRequest("request");
    Context parentContext = Context.root().with(CALLBACK_CONTEXT, "parent");
    Context operationContext = parentContext.with(OPERATION_CONTEXT, "operation");
    when(instrumenter.start(parentContext, request)).thenReturn(operationContext);

    TracedOnSubscribe<String, TestRequest> traced =
        tracedOnSubscribe(source, request, parentContext);
    RecordingSubscriber subscriber = new RecordingSubscriber();
    traced.call(subscriber);

    subscriber.unsubscribe();
    subscriber.unsubscribe();
    source.subscribers.get(0).onCompleted();

    assertThat(subscriber.completionCount).isEqualTo(1);
    verify(instrumenter).start(parentContext, request);
    verify(instrumenter).end(operationContext, request, null, null);
    verifyNoMoreInteractions(instrumenter);
  }

  @Test
  void isolatesRequestsAcrossPerSubscriptionReuse() {
    TestSource source = new TestSource();
    Context parentContext = Context.root().with(CALLBACK_CONTEXT, "parent");
    AtomicInteger requestId = new AtomicInteger();
    List<TestRequest> requests = new ArrayList<>();
    List<Context> operationContexts = new ArrayList<>();
    when(instrumenter.start(eq(parentContext), any()))
        .thenAnswer(
            invocation -> {
              TestRequest request = invocation.getArgument(1);
              Context operationContext = parentContext.with(OPERATION_CONTEXT, request.name);
              operationContexts.add(operationContext);
              return operationContext;
            });

    TracedOnSubscribe<String, TestRequest> traced;
    try (Scope ignored = parentContext.makeCurrent()) {
      traced =
          TracedOnSubscribe.perSubscription(
              source.observable(),
              instrumenter,
              () -> {
                TestRequest request = new TestRequest("request-" + requestId.incrementAndGet());
                requests.add(request);
                return request;
              });
    }
    RecordingSubscriber firstSubscriber = new RecordingSubscriber();
    RecordingSubscriber secondSubscriber = new RecordingSubscriber();

    traced.call(firstSubscriber);
    traced.call(secondSubscriber);
    source.subscribers.get(1).onCompleted();
    source.subscribers.get(0).onCompleted();

    assertThat(requests)
        .extracting(request -> request.name)
        .containsExactly("request-1", "request-2");
    assertThat(firstSubscriber.onCompletedContext).isSameAs(operationContexts.get(0));
    assertThat(secondSubscriber.onCompletedContext).isSameAs(operationContexts.get(1));
    verify(instrumenter).start(parentContext, requests.get(0));
    verify(instrumenter).start(parentContext, requests.get(1));
    verify(instrumenter).end(operationContexts.get(0), requests.get(0), null, null);
    verify(instrumenter).end(operationContexts.get(1), requests.get(1), null, null);
    verifyNoMoreInteractions(instrumenter);
  }

  private TracedOnSubscribe<String, TestRequest> tracedOnSubscribe(
      TestSource source, TestRequest request, Context parentContext) {
    try (Scope ignored = parentContext.makeCurrent()) {
      return new TracedOnSubscribe<>(source.observable(), instrumenter, request);
    }
  }

  private static final class TestSource implements Observable.OnSubscribe<String> {
    private final List<Subscriber<? super String>> subscribers = new ArrayList<>();

    private Observable<String> observable() {
      return Observable.create(this);
    }

    @Override
    public void call(Subscriber<? super String> subscriber) {
      subscribers.add(subscriber);
    }
  }

  private static final class RecordingSubscriber extends Subscriber<String> {
    private final List<String> values = new ArrayList<>();
    private Context onNextContext;
    private Context onCompletedContext;
    private Context onErrorContext;
    private Throwable error;
    private int completionCount;

    @Override
    public void onNext(String value) {
      values.add(value);
      onNextContext = Context.current();
    }

    @Override
    public void onCompleted() {
      completionCount++;
      onCompletedContext = Context.current();
    }

    @Override
    public void onError(Throwable e) {
      error = e;
      onErrorContext = Context.current();
    }
  }

  private static final class TestRequest {
    private final String name;

    private TestRequest(String name) {
      this.name = name;
    }
  }
}
