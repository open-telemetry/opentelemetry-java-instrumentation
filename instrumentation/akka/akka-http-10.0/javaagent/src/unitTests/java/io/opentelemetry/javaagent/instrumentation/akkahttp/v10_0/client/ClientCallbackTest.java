/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client.HttpExtClientInstrumentation.SingleRequestAdvice.AdviceScope;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.impl.Promise;
import scala.runtime.AbstractFunction1;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

class ClientCallbackTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void completesPendingResponse(boolean reject, boolean fail)
      throws InterruptedException, TimeoutException {
    TestExecutionContext executionContext = new TestExecutionContext();
    MessageDispatcher dispatcher = mock(MessageDispatcher.class);
    when(dispatcher.prepare()).thenReturn(dispatcher);
    doAnswer(
            invocation -> {
              executionContext.execute(invocation.getArgument(0));
              return null;
            })
        .when(dispatcher)
        .execute(any());
    doAnswer(
            invocation -> {
              executionContext.reportFailure(invocation.getArgument(0));
              return null;
            })
        .when(dispatcher)
        .reportFailure(any());
    ActorSystem actorSystem = mock(ActorSystem.class);
    when(actorSystem.dispatcher()).thenReturn(dispatcher);

    HttpRequest request =
        (HttpRequest) akka.http.javadsl.model.HttpRequest.create("http://localhost/");
    HttpResponse response = (HttpResponse) akka.http.javadsl.model.HttpResponse.create();
    IllegalStateException failure = new IllegalStateException("original response failure");
    Try<HttpResponse> result = fail ? new Failure<>(failure) : new Success<>(response);
    Promise.DefaultPromise<HttpResponse> original = new Promise.DefaultPromise<>();
    AtomicInteger completions = new AtomicInteger();
    AtomicReference<Context> callbackContext = new AtomicReference<>();
    ContextKey<String> key = ContextKey.named("callback-context");
    Context parent = Context.root().with(key, "parent");
    Context completing = Context.root().with(key, "completing");
    Context previous = Context.current();
    SpanContext clientSpan;
    Future<HttpResponse> wrapped;
    try (Scope ignored = parent.makeCurrent()) {
      AdviceScope adviceScope = AdviceScope.start(request);
      clientSpan = Span.current().getSpanContext();
      wrapped = adviceScope.end(actorSystem, request, original, null);
      assertThat(Context.current()).isSameAs(parent);
      wrapped.onComplete(
          new AbstractFunction1<Try<HttpResponse>, Object>() {
            @Override
            public Object apply(Try<HttpResponse> value) {
              assertThat(value).isEqualTo(result);
              callbackContext.set(Context.current());
              completions.incrementAndGet();
              return null;
            }
          },
          new TestExecutionContext());
    }
    assertThat(Context.current()).isSameAs(previous);
    assertThat(wrapped.isCompleted()).isFalse();

    // Rejection begins after registration, when the original response is still pending.
    executionContext.reject = reject;
    try (Scope ignored = completing.makeCurrent()) {
      original.complete(result);
      assertThat(Context.current()).isSameAs(completing);
    }
    executionContext.reject = false;

    assertThat(Await.ready(wrapped, Duration.create(5, SECONDS))).isSameAs(wrapped);
    assertThat(wrapped.value().get()).isEqualTo(result);
    if (fail) {
      assertThat(wrapped.value().get().failed().get()).isSameAs(failure);
    } else {
      assertThat(wrapped.value().get().get()).isSameAs(response);
    }
    assertThat(callbackContext.get()).isSameAs(parent);
    assertThat(completions.get()).isEqualTo(1);
    assertThat(original.tryComplete(result)).isFalse();
    assertThat(Context.current()).isSameAs(previous);
    assertThat(executionContext.failures).hasSize(reject ? 2 : 0);
    assertThat(executionContext.failures)
        .allSatisfy(error -> assertThat(error).isInstanceOf(RejectedExecutionException.class));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasSpanId(clientSpan.getSpanId()).hasKind(SpanKind.CLIENT).hasNoParent();
                  if (fail) {
                    span.hasException(failure);
                  } else {
                    span.hasStatus(StatusData.unset()).hasEventsSatisfyingExactly();
                  }
                }));
  }

  private static final class TestExecutionContext implements ExecutionContext {
    private final List<Throwable> failures = new ArrayList<>();
    private boolean reject;

    @Override
    public void execute(Runnable runnable) {
      if (reject) {
        throw new RejectedExecutionException("dispatcher queue full");
      }
      runnable.run();
    }

    @Override
    public void reportFailure(Throwable cause) {
      failures.add(cause);
    }

    @Override
    public ExecutionContext prepare() {
      return this;
    }
  }
}
