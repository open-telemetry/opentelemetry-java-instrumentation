/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.instrumenter.SpanExceptionHandler;
import java.sql.SQLException;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class JdbcSanitizingSpanExceptionHandler implements SpanExceptionHandler {

  static final JdbcSanitizingSpanExceptionHandler INSTANCE =
      new JdbcSanitizingSpanExceptionHandler();

  @Override
  public void handle(Span span, Throwable throwable) {
    if (!(throwable instanceof SQLException)) {
      span.recordException(throwable);
      return;
    }
    SQLException sql = (SQLException) throwable;
    String state = sql.getSQLState();
    String sanitizedMsg =
        "SQL error [" + sql.getErrorCode() + (state != null ? "/" + state : "") + "]";

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

  private JdbcSanitizingSpanExceptionHandler() {}
}
