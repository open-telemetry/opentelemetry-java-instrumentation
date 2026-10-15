/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.SchemaUrls;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpServerExceptionRecorder {

  @Nullable private final Logger logger;

  public static HttpServerExceptionRecorder create(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return new HttpServerExceptionRecorder(openTelemetry, instrumentationName);
  }

  private HttpServerExceptionRecorder(OpenTelemetry openTelemetry, String instrumentationName) {
    if (emitExceptionAsLogs()) {
      LoggerBuilder loggerBuilder =
          openTelemetry.getLogsBridge().loggerBuilder(instrumentationName);
      String instrumentationVersion =
          EmbeddedInstrumentationProperties.findVersion(instrumentationName);
      if (instrumentationVersion != null) {
        loggerBuilder.setInstrumentationVersion(instrumentationVersion);
      }
      logger = loggerBuilder.setSchemaUrl(SchemaUrls.V1_41_0).build();
    } else {
      logger = null;
    }
  }

  public void recordException(Context context, Span span, Throwable throwable) {
    if (emitExceptionAsSpanEvents()) {
      span.recordException(throwable);
    }
    if (logger != null) {
      logger
          .logRecordBuilder()
          .setContext(context.with(span))
          .setEventName("http.server.request.exception")
          .setSeverity(Severity.ERROR)
          .setException(throwable)
          .emit();
    }
  }
}
