/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support.async;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Jdk8AsyncOperationEndStrategyTest {
  @Mock Instrumenter<String, String> instrumenter;

  AsyncOperationEndStrategy underTest = Jdk8AsyncOperationEndStrategy.INSTANCE;

  @Test
  void shouldEndOperationOnAlreadyCompletedFuture() {
    // given
    Context context = Context.root();

    // when
    underTest.end(
        instrumenter, context, "request", CompletableFuture.completedFuture("done!"), String.class);

    // then
    verify(instrumenter).end(context, "request", "done!", null);
  }

  @Test
  void shouldEndOperationOnAlreadyFailedFuture() {
    // given
    Context context = Context.root();

    CompletableFuture<String> future = new CompletableFuture<>();
    Exception exception = new CompletionException(new RuntimeException("boom!"));
    future.completeExceptionally(exception);

    // when
    underTest.end(instrumenter, context, "request", future, String.class);

    // then
    verify(instrumenter).end(context, "request", null, exception);
  }

  @Test
  void shouldNotPassResponseIfItHasDifferentTypeThanExpected() {
    // given
    Context context = Context.root();

    // when
    underTest.end(
        instrumenter, context, "request", CompletableFuture.completedFuture(42), String.class);

    // then
    verify(instrumenter).end(context, "request", null, null);
  }

  @Test
  void shouldEndOperationWhenFutureCompletes() {
    // given
    Context context = Context.root();

    CompletableFuture<String> future = new CompletableFuture<>();

    // when
    underTest.end(instrumenter, context, "request", future, String.class);

    // then
    verifyNoInteractions(instrumenter);

    // when
    future.complete("done!");

    // then
    verify(instrumenter).end(context, "request", "done!", null);
  }

  @Test
  void shouldEndOperationWhenFutureFails() {
    // given
    Context context = Context.root();

    CompletableFuture<String> future = new CompletableFuture<>();

    // when
    underTest.end(instrumenter, context, "request", future, String.class);

    // then
    verifyNoInteractions(instrumenter);

    // when
    Exception exception = new CompletionException(new RuntimeException("boom!"));
    future.completeExceptionally(exception);

    // then
    verify(instrumenter).end(context, "request", null, exception);
  }
}
