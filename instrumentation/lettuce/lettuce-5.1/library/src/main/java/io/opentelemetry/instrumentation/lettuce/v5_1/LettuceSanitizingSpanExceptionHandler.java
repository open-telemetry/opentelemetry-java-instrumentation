/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import io.lettuce.core.RedisCommandExecutionException;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.instrumenter.SpanExceptionHandler;

final class LettuceSanitizingSpanExceptionHandler implements SpanExceptionHandler {

  static final LettuceSanitizingSpanExceptionHandler INSTANCE =
      new LettuceSanitizingSpanExceptionHandler();

  @Override
  public void handle(Span span, Throwable throwable) {
    if (!(throwable instanceof RedisCommandExecutionException)) {
      span.recordException(throwable);
      return;
    }
    String sanitizedMsg = "<redacted>";

    StringBuilder stackTrace = new StringBuilder();
    stackTrace.append(throwable.getClass().getName()).append(": ").append(sanitizedMsg);
    for (StackTraceElement element : throwable.getStackTrace()) {
      stackTrace.append("\n\tat ").append(element);
    }

    span.addEvent(
        "exception",
        Attributes.of(
            EXCEPTION_TYPE, throwable.getClass().getName(),
            EXCEPTION_MESSAGE, sanitizedMsg,
            EXCEPTION_STACKTRACE, stackTrace.toString()));
  }

  private LettuceSanitizingSpanExceptionHandler() {}
}
