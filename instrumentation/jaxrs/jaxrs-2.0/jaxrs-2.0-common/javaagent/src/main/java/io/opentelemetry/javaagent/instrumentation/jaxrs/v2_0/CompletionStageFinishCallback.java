/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxRsAnnotationsTracer.tracer;

import io.opentelemetry.context.Context;
import java.util.function.BiFunction;

public class CompletionStageFinishCallback<T> implements BiFunction<T, Throwable, T> {
  private final Context context;

  public CompletionStageFinishCallback(Context context) {
    this.context = context;
  }

  @Override
  public T apply(T result, Throwable throwable) {
    if (throwable == null) {
      tracer().end(context);
    } else {
      tracer().endExceptionally(context, throwable);
    }
    return result;
  }
}
