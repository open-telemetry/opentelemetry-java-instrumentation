/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

public final class CompletionStageFinishCallback<T> implements BiFunction<T, Throwable, T> {
  private final Instrumenter<HandlerData, Void> instrumenter;
  private final Context context;
  private final HandlerData handlerData;

  public CompletionStageFinishCallback(
      Instrumenter<HandlerData, Void> instrumenter, Context context, HandlerData handlerData) {
    this.instrumenter = instrumenter;
    this.context = context;
    this.handlerData = handlerData;
  }

  @Override
  @CanIgnoreReturnValue
  @Nullable
  public T apply(@Nullable T result, @Nullable Throwable throwable) {
    instrumenter.end(context, handlerData, null, throwable);
    return result;
  }
}
