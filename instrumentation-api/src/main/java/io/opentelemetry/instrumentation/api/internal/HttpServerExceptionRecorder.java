/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
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

  private final Logger logger;

  public static HttpServerExceptionRecorder create(
      OpenTelemetry openTelemetry, String instrumentationName, @Nullable String schemaUrl) {
    LoggerBuilder builder = openTelemetry.getLogsBridge().loggerBuilder(instrumentationName);
    String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (version != null) {
      builder.setInstrumentationVersion(version);
    }
    if (schemaUrl != null) {
      builder.setSchemaUrl(schemaUrl);
    }
    return new HttpServerExceptionRecorder(builder.build());
  }

  private HttpServerExceptionRecorder(Logger logger) {
    this.logger = logger;
  }

  /**
   * Immediately records an HTTP server exception using the selected signals.
   *
   * @param context the reporting context for the exception log
   * @param eventSpan the span receiving exception events, or {@code null} if unavailable
   * @param error the already-extracted exception, emitted without further cause extraction
   */
  public void recordException(Context context, @Nullable Span eventSpan, Throwable error) {
    ExceptionRecorder.recordException(
        context, eventSpan, error, logger, serverExceptionEventExtractor, error, null);
  }
}
