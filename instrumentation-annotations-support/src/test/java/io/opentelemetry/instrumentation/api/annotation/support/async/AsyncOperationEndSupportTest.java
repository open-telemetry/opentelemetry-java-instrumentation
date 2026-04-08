/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsyncOperationEndSupportTest {
  @Mock Instrumenter<String, String> instrumenter;

  @Test
  void shouldEndImmediatelyWhenExceptionWasPassed() {
    // given
    AsyncOperationEndSupport<String, String> underTest =
        AsyncOperationEndSupport.create(instrumenter, String.class, CompletableFuture.class);

    Context context = Context.root();
    Exception exception = new RuntimeException("boom!");
    CompletableFuture<String> future = new CompletableFuture<>();

    // when
    CompletableFuture<String> result = underTest.asyncEnd(context, "request", future, exception);

    // then
    assertThat(result).isSameAs(future);

    verify(instrumenter).end(context, "request", null, exception);
  }

  @Test
  void shouldEndImmediatelyWhenWrongReturnTypeWasPassed() {
    // given
    AsyncOperationEndSupport<String, String> underTest =
        AsyncOperationEndSupport.create(instrumenter, String.class, Future.class);

    Context context = Context.root();
    CompletableFuture<String> future = new CompletableFuture<>();

    // when
    CompletableFuture<String> result = underTest.asyncEnd(context, "request", future, null);

    // then
    assertThat(result).isSameAs(future);

    verify(instrumenter).end(context, "request", null, null);
  }

  @Test
  void shouldEndImmediatelyWhenAsyncWrapperIsOfWrongType() {
    // given
    AsyncOperationEndSupport<String, String> underTest =
        AsyncOperationEndSupport.create(instrumenter, String.class, CompletableFuture.class);

    Context context = Context.root();

    // when
    String result = underTest.asyncEnd(context, "request", "not async", null);

    // then
    assertThat(result).isSameAs("not async");

    verify(instrumenter).end(context, "request", "not async", null);
  }

  @Test
  void shouldReturnedDecoratedAsyncWrapper() {
    // given
    AsyncOperationEndSupport<String, String> underTest =
        AsyncOperationEndSupport.create(instrumenter, String.class, CompletionStage.class);

    Context context = Context.root();
    CompletableFuture<String> future = new CompletableFuture<>();

    // when
    CompletableFuture<String> result = underTest.asyncEnd(context, "request", future, null);

    // then
    assertThat(result).isNotSameAs(future);
    verifyNoInteractions(instrumenter);

    // when
    future.complete("done!");

    // then
    assertThat(result.join()).isEqualTo("done!");
    verify(instrumenter).end(context, "request", "done!", null);
  }
}
