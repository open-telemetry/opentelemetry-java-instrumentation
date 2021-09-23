/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsSingletons.instrumenter;

import io.opentelemetry.context.Context;
import java.util.function.BiFunction;

public class CompletionStageFinishCallback<T> implements BiFunction<T, Throwable, T> {
  private final Context context;
  private final HandlerData handlerData;

  public CompletionStageFinishCallback(Context context, HandlerData handlerData) {
    this.context = context;
    this.handlerData = handlerData;
  }

  @Override
  public T apply(T result, Throwable throwable) {
    instrumenter().end(context, handlerData, null, throwable);
    return result;
  }
}
