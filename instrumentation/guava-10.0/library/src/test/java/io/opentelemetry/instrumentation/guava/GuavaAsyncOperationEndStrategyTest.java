/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuavaAsyncOperationEndStrategyTest {

  @Mock private Instrumenter<String, String> instrumenter;

  @Mock private Span span;

  private final AsyncOperationEndStrategy strategy = GuavaAsyncOperationEndStrategy.create();

  @Test
  void listenableFutureSupported() {
    assertThat(strategy.supports(ListenableFuture.class)).isTrue();
  }

  @Test
  void settableFutureSupported() {
    assertThat(strategy.supports(SettableFuture.class)).isTrue();
  }

  @Test
  void endsSpanOnSuccess() {
    SettableFuture<String> future = SettableFuture.create();

    strategy.end(instrumenter, Context.root(), "request", future, String.class);
    future.set("response");

    verify(instrumenter).end(Context.root(), "request", "response", null);
  }

  @Test
  void endsSpanOnFailure() {
    SettableFuture<String> future = SettableFuture.create();
    IllegalStateException error = new IllegalStateException();

    strategy.end(instrumenter, Context.root(), "request", future, String.class);
    future.setException(error);

    verify(instrumenter)
        .end(
            eq(Context.root()),
            eq("request"),
            isNull(),
            argThat(
                val -> {
                  assertThat(val).hasCause(error);
                  return true;
                }));
  }

  @Test
  void endsSpanOnCancel() {
    when(span.storeInContext(any())).thenCallRealMethod();

    SettableFuture<String> future = SettableFuture.create();
    Context context = Context.root().with(span);

    strategy.end(instrumenter, context, "request", future, String.class);
    future.cancel(true);

    verify(instrumenter).end(context, "request", null, null);
  }

  @Test
  void endsSpanOnCancelExperimentalAttribute() {
    when(span.storeInContext(any())).thenCallRealMethod();
    when(span.setAttribute(GuavaAsyncOperationEndStrategy.CANCELED_ATTRIBUTE_KEY, true))
        .thenReturn(span);

    SettableFuture<String> future = SettableFuture.create();
    Context context = Context.root().with(span);

    AsyncOperationEndStrategy strategy =
        GuavaAsyncOperationEndStrategy.builder().setCaptureExperimentalSpanAttributes(true).build();

    strategy.end(instrumenter, context, "request", future, String.class);
    future.cancel(true);

    verify(instrumenter).end(context, "request", null, null);
  }

  @Test
  void endsSpanOnImmediateSuccess() {
    ListenableFuture<String> future = Futures.immediateFuture("response");

    strategy.end(instrumenter, Context.root(), "request", future, String.class);

    verify(instrumenter).end(Context.root(), "request", "response", null);
  }

  @Test
  void endsSpanOnImmediateFailure() {
    IllegalStateException error = new IllegalStateException();
    ListenableFuture<String> future = Futures.immediateFailedFuture(error);

    strategy.end(instrumenter, Context.root(), "request", future, String.class);

    verify(instrumenter)
        .end(
            eq(Context.root()),
            eq("request"),
            isNull(),
            argThat(
                val -> {
                  assertThat(val).hasCause(error);
                  return true;
                }));
  }
}
