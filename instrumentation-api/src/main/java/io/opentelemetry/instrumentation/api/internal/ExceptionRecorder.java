/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExceptionRecorder<REQUEST> {

  private final boolean emitAsSpanEvents;
  @Nullable private final Logger logger;
  @Nullable private final InternalExceptionEventExtractor<? super REQUEST> eventExtractor;

  public static <REQUEST> ExceptionRecorder<REQUEST> create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      @Nullable String instrumentationVersion,
      @Nullable String schemaUrl,
      @Nullable InternalExceptionEventExtractor<? super REQUEST> eventExtractor) {
    @Nullable
    Logger logger =
        emitExceptionAsLogs()
            ? buildLogger(openTelemetry, instrumentationName, instrumentationVersion, schemaUrl)
            : null;
    return new ExceptionRecorder<>(logger, eventExtractor);
  }

  private ExceptionRecorder(
      @Nullable Logger logger,
      @Nullable InternalExceptionEventExtractor<? super REQUEST> eventExtractor) {
    this.emitAsSpanEvents = emitExceptionAsSpanEvents();
    this.logger = logger;
    this.eventExtractor = eventExtractor;
  }

  public void recordException(
      Context context,
      Span span,
      Throwable throwable,
      REQUEST request,
      @Nullable Instant timestamp) {
    if (emitAsSpanEvents) {
      span.recordException(throwable);
    }
    if (logger != null && eventExtractor != null) {
      LogRecordBuilder logRecordBuilder = logger.logRecordBuilder();
      logRecordBuilder.setContext(context.with(span));
      if (timestamp != null) {
        logRecordBuilder.setTimestamp(timestamp);
      }
      eventExtractor.extract(logRecordBuilder, context, request);
      logRecordBuilder.setException(throwable);
      logRecordBuilder.emit();
    }
  }

  private static Logger buildLogger(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      @Nullable String instrumentationVersion,
      @Nullable String schemaUrl) {
    LoggerBuilder loggerBuilder = openTelemetry.getLogsBridge().loggerBuilder(instrumentationName);
    if (instrumentationVersion != null) {
      loggerBuilder.setInstrumentationVersion(instrumentationVersion);
    }
    if (schemaUrl != null) {
      loggerBuilder.setSchemaUrl(schemaUrl);
    }
    return loggerBuilder.build();
  }
}
