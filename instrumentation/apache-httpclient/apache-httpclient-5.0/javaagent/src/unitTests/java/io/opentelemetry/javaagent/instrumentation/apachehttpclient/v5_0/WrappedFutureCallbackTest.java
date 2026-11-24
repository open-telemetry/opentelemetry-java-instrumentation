/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpAsyncClientInstrumentation.WrappedFutureCallback;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Test;

class WrappedFutureCallbackTest {

  private static final ContextKey<String> KEY = ContextKey.named("callback-context");

  private final Context callerContext = Context.root().with(KEY, "caller");
  private final Context callbackContext = Context.root().with(KEY, "callback");
  private final FutureCallback<?> delegate = mock(FutureCallback.class);
  private final WrappedFutureCallback<?> callback =
      new WrappedFutureCallback<>(callerContext, new BasicHttpContext(), delegate);

  @Test
  void failureBeforeSpanStartRestoresCallbackContext() {
    Exception failure = new IllegalStateException("request failed");
    doAnswer(
            invocation -> {
              assertThat(Context.current()).isSameAs(callerContext);
              return null;
            })
        .when(delegate)
        .failed(failure);

    try (Scope ignored = callbackContext.makeCurrent()) {
      callback.failed(failure);
      assertThat(Context.current()).isSameAs(callbackContext);
    }
    verify(delegate).failed(failure);
  }

  @Test
  void cancellationBeforeSpanStartRestoresCallbackContextIfDelegateThrows() {
    RuntimeException failure = new IllegalStateException("delegate failed");
    doAnswer(
            invocation -> {
              assertThat(Context.current()).isSameAs(callerContext);
              throw failure;
            })
        .when(delegate)
        .cancelled();

    try (Scope ignored = callbackContext.makeCurrent()) {
      assertThatThrownBy(callback::cancelled).isSameAs(failure);
      assertThat(Context.current()).isSameAs(callbackContext);
    }
  }
}
