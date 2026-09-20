/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CompletableFutureWrapperTest {

  @Test
  void propagatesCancellation() {
    CompletableFuture<String> sourceFuture = new CompletableFuture<>();
    CompletableFuture<String> wrapped =
        CompletableFutureWrapper.wrap(sourceFuture, Context.root(), (result, error) -> {});

    assertThat(wrapped.cancel(false)).isTrue();
    assertThat(wrapped).isCancelled();
    assertThat(sourceFuture).isCancelled();
  }

  @Test
  void finishesInstrumentationBeforeCancellationCallbacks() {
    List<String> events = new ArrayList<>();
    CompletableFuture<String> sourceFuture = new CompletableFuture<>();
    CompletableFuture<String> wrapped =
        CompletableFutureWrapper.wrap(
            sourceFuture, Context.root(), (result, error) -> events.add("instrumentation"));
    wrapped.whenComplete((result, error) -> events.add("user"));

    assertThat(wrapped.cancel(false)).isTrue();

    assertThat(events).containsExactly("instrumentation", "user");
  }

  @Test
  void cancellationCallbacksUseCapturedContext() {
    ContextKey<String> key = ContextKey.named("test-key");
    Context context = Context.root().with(key, "test-value");
    CompletableFuture<String> sourceFuture = new CompletableFuture<>();
    CompletableFuture<String> wrapped =
        CompletableFutureWrapper.wrap(sourceFuture, context, (result, error) -> {});
    AtomicReference<String> callbackValue = new AtomicReference<>();
    wrapped.whenComplete((result, error) -> callbackValue.set(Context.current().get(key)));

    assertThat(wrapped.cancel(false)).isTrue();

    assertThat(callbackValue).hasValue("test-value");
  }

  @Test
  void sourceCancellationRefusalDoesNotCancelWrapper() throws InterruptedException {
    CountDownLatch completionStarted = new CountDownLatch(1);
    CountDownLatch allowCompletion = new CountDownLatch(1);
    CompletableFuture<String> sourceFuture = new CompletableFuture<>();
    CompletableFuture<String> wrapped =
        CompletableFutureWrapper.wrap(
            sourceFuture,
            Context.root(),
            (result, error) -> {
              completionStarted.countDown();
              try {
                allowCompletion.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
              }
            });
    Thread completer = new Thread(() -> sourceFuture.complete("result"));
    completer.start();

    try {
      assertThat(completionStarted.await(10, SECONDS)).isTrue();
      assertThat(wrapped.cancel(false)).isFalse();
      assertThat(wrapped).isNotCancelled();
    } finally {
      allowCompletion.countDown();
      completer.join();
    }

    assertThat(wrapped).isCompletedWithValue("result");
  }

  @Test
  void cancellingDerivedStageDoesNotCancelSource() {
    CompletableFuture<String> sourceFuture = new CompletableFuture<>();
    CompletableFuture<String> wrapped =
        CompletableFutureWrapper.wrap(sourceFuture, Context.root(), (result, error) -> {});
    CompletableFuture<Integer> derived = wrapped.thenApply(String::length);

    assertThat(derived.cancel(false)).isTrue();
    assertThat(sourceFuture).isNotCancelled();

    sourceFuture.complete("result");

    assertThat(wrapped).isCompletedWithValue("result");
    assertThat(derived).isCancelled();
  }

  @Test
  void finishesInstrumentationOnce() {
    AtomicInteger completionCount = new AtomicInteger();
    CompletableFuture<String> sourceFuture = new CompletableFuture<>();
    CompletableFuture<String> wrapped =
        CompletableFutureWrapper.wrap(
            sourceFuture, Context.root(), (result, error) -> completionCount.incrementAndGet());

    assertThat(sourceFuture.complete("result")).isTrue();
    assertThat(sourceFuture.complete("other")).isFalse();
    assertThat(sourceFuture.completeExceptionally(new IllegalStateException("test"))).isFalse();

    assertThat(completionCount).hasValue(1);
    assertThat(wrapped).isCompletedWithValue("result");
  }

  @Test
  void completesWithCapturedContext() {
    ContextKey<String> key = ContextKey.named("test-key");
    Context context = Context.root().with(key, "test-value");
    CompletableFuture<String> sourceFuture = new CompletableFuture<>();
    CompletableFuture<String> wrapped =
        CompletableFutureWrapper.wrap(sourceFuture, context, (result, error) -> {});
    AtomicReference<String> callbackValue = new AtomicReference<>();
    wrapped.thenRun(() -> callbackValue.set(Context.current().get(key)));

    sourceFuture.complete("result");

    assertThat(wrapped).isCompletedWithValue("result");
    assertThat(callbackValue).hasValue("test-value");
  }

  @Test
  void completionFailureDoesNotReplaceResult() {
    CompletableFuture<String> future = new CompletableFuture<>();
    CompletableFuture<String> wrapped =
        CompletableFutureWrapper.wrap(
            future,
            Context.root(),
            (result, error) -> {
              throw new IllegalStateException("test");
            });

    future.complete("result");

    assertThat(wrapped).isCompletedWithValue("result");
  }
}
