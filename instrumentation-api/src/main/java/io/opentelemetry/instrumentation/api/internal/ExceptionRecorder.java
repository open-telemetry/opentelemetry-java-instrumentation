/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExceptionRecorder {

  public static <REQUEST> void recordException(
      Context context,
      Span span,
      Throwable throwable,
      @Nullable Logger logger,
      @Nullable InternalExceptionEventExtractor<? super REQUEST> exceptionEventExtractor,
      REQUEST request,
      @Nullable Instant timestamp) {
    if (emitExceptionAsSpanEvents()) {
      span.recordException(throwable);
    }
    // Exception logs are intentionally emitted even when the span is not recording.
    if (emitExceptionAsLogs() && logger != null && exceptionEventExtractor != null) {
      LogRecordBuilder logRecordBuilder = logger.logRecordBuilder();
      logRecordBuilder.setContext(context);
      if (timestamp != null) {
        logRecordBuilder.setTimestamp(timestamp);
      }
      exceptionEventExtractor.extract(logRecordBuilder, context, request);
      logRecordBuilder.setException(throwable);
      logRecordBuilder.emit();
    }
  }

  private ExceptionRecorder() {}
}
