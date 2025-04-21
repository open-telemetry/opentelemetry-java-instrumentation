/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava.v10_0;

import static io.opentelemetry.instrumentation.api.annotation.support.async.AsyncEndSupport.tryToGetResponse;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncEndHandler;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncEndStrategy;

public final class GuavaAsyncEndStrategy implements AsyncEndStrategy {
  // Visible for testing
  static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("guava.canceled");

  public static GuavaAsyncEndStrategy create() {
    return builder().build();
  }

  public static GuavaAsyncEndStrategyBuilder builder() {
    return new GuavaAsyncEndStrategyBuilder();
  }

  private final boolean captureExperimentalSpanAttributes;

  GuavaAsyncEndStrategy(boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  public boolean supports(Class<?> returnType) {
    return ListenableFuture.class.isAssignableFrom(returnType);
  }

  @Override
  public <REQUEST, RESPONSE> Object end(
      AsyncEndHandler<REQUEST, RESPONSE> handler,
      Context context,
      REQUEST request,
      Object asyncValue,
      Class<RESPONSE> responseType) {

    ListenableFuture<?> future = (ListenableFuture<?>) asyncValue;
    end(handler, context, request, future, responseType);
    return future;
  }

  private <REQUEST, RESPONSE> void end(
      AsyncEndHandler<REQUEST, RESPONSE> handler,
      Context context,
      REQUEST request,
      ListenableFuture<?> future,
      Class<RESPONSE> responseType) {
    if (future.isDone()) {
      if (future.isCancelled()) {
        if (captureExperimentalSpanAttributes) {
          Span.fromContext(context).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
        }
        handler.handle(context, request, null, null);
      } else {
        try {
          Object response = Uninterruptibles.getUninterruptibly(future);
          handler.handle(context, request, tryToGetResponse(responseType, response), null);
        } catch (Throwable exception) {
          handler.handle(context, request, null, exception);
        }
      }
    } else {
      future.addListener(() -> end(handler, context, request, future, responseType), Runnable::run);
    }
  }
}
