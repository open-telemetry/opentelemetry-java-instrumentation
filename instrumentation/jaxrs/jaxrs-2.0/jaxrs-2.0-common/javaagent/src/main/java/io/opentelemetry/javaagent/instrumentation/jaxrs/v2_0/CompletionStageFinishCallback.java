/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxRsAnnotationsTracer.tracer;

import io.opentelemetry.api.trace.Span;
import java.util.function.BiFunction;

public class CompletionStageFinishCallback<T> implements BiFunction<T, Throwable, T> {
  private final Span span;

  public CompletionStageFinishCallback(Span span) {
    this.span = span;
  }

  @Override
  public T apply(T result, Throwable throwable) {
    if (throwable == null) {
      tracer().end(span);
    } else {
      tracer().endExceptionally(span, throwable);
    }
    return result;
  }
}
