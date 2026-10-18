/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * Records HTTP server exceptions through the shared exception signal emission helper.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class HttpServerExceptionRecorder {
  private static final InternalExceptionEventExtractor<Throwable> serverExceptionEventExtractor =
      (log, context, unused) ->
          log.setEventName("http.server.request.exception").setSeverity(Severity.ERROR);

  /** Records the observed exception using default cause extraction and the reporting timestamp. */
  public static void recordException(
      Context context, @Nullable Span eventSpan, Throwable error, @Nullable Logger logger) {
    recordException(context, eventSpan, error, ErrorCauseExtractor.getDefault(), logger);
  }

  /**
   * Records an HTTP server exception using the selected signals and the reporting timestamp.
   *
   * @param context the reporting context for the exception log
   * @param eventSpan the span receiving exception events, or {@code null} if unavailable
   * @param error the observed exception
   * @param causeExtractor extracts the exception once before either signal is emitted; replaces,
   *     rather than supplements, default extraction
   * @param logger the framework logger, or {@code null} if log emission is unavailable
   */
  public static void recordException(
      Context context,
      @Nullable Span eventSpan,
      Throwable error,
      ErrorCauseExtractor causeExtractor,
      @Nullable Logger logger) {
    Instant timestamp = emitExceptionAsLogs() ? Instant.now() : null;
    error = causeExtractor.extract(error);
    ExceptionRecorder.recordException(
        context, eventSpan, error, logger, serverExceptionEventExtractor, error, timestamp);
  }

  private HttpServerExceptionRecorder() {}
}
