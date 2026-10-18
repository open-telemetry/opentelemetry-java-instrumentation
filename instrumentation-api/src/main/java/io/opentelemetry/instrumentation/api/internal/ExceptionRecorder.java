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
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * Shared exception signal emission mechanics. Callers select the span-event target. The HTTP server
 * entry point applies the supplied cause extractor; the generic entry point emits the supplied
 * exception without extraction.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ExceptionRecorder {
  private static final InternalExceptionEventExtractor<Throwable> serverExceptionEventExtractor =
      (log, context, unused) ->
          log.setEventName("http.server.request.exception").setSeverity(Severity.ERROR);

  public static Logger createLogger(
      OpenTelemetry openTelemetry, String instrumentationName, @Nullable String schemaUrl) {
    LoggerBuilder builder = openTelemetry.getLogsBridge().loggerBuilder(instrumentationName);
    String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (version != null) {
      builder.setInstrumentationVersion(version);
    }
    if (schemaUrl != null) {
      builder.setSchemaUrl(schemaUrl);
    }
    return builder.build();
  }

  /**
   * Records an HTTP server exception using the selected signals.
   *
   * @param context the reporting context for the exception log
   * @param eventSpan the span receiving exception events, or {@code null} if unavailable
   * @param error the observed exception
   * @param causeExtractor extracts the exception once before either signal is emitted; replaces,
   *     rather than supplements, default extraction
   * @param logger the framework logger, or {@code null} if log emission is unavailable
   */
  public static void recordServerException(
      Context context,
      @Nullable Span eventSpan,
      Throwable error,
      ErrorCauseExtractor causeExtractor,
      @Nullable Logger logger) {
    Instant timestamp = emitExceptionAsLogs() ? Instant.now() : null;
    error = causeExtractor.extract(error);
    recordException(
        context, eventSpan, error, logger, serverExceptionEventExtractor, error, timestamp);
  }

  /**
   * Emits the already-extracted {@code error} without further cause extraction. The log uses {@code
   * context}, {@code logger}, {@code eventExtractor}, {@code request}, and the explicit {@code
   * timestamp} when present; exception span events target {@code eventSpan} when available.
   */
  public static <REQUEST> void recordException(
      Context context,
      @Nullable Span eventSpan,
      Throwable error,
      @Nullable Logger logger,
      @Nullable InternalExceptionEventExtractor<? super REQUEST> eventExtractor,
      REQUEST request,
      @Nullable Instant timestamp) {
    if (emitExceptionAsSpanEvents() && eventSpan != null) {
      eventSpan.recordException(error);
    }
    // Logs do not depend on a recording span, or even the presence of a span.
    if (!emitExceptionAsLogs() || logger == null || eventExtractor == null) {
      return;
    }
    LogRecordBuilder log = logger.logRecordBuilder().setContext(context);
    if (timestamp != null) {
      log.setTimestamp(timestamp);
    }
    eventExtractor.extract(log, context, request);
    log.setException(error).emit();
  }

  private ExceptionRecorder() {}
}
