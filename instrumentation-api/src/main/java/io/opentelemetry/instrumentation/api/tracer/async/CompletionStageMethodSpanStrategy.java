/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.util.concurrent.CompletionStage;

enum CompletionStageMethodSpanStrategy implements MethodSpanStrategy {
  INSTANCE;

  @Override
  public Object end(Object result, BaseTracer tracer, Context context) {
    if (result instanceof CompletionStage) {
      CompletionStage<?> stage = (CompletionStage<?>) result;
      return stage.whenComplete(
          (value, error) -> {
            if (error != null) {
              tracer.endExceptionally(context, error);
            } else {
              tracer.end(context);
            }
          });
    }
    return result;
  }
}
