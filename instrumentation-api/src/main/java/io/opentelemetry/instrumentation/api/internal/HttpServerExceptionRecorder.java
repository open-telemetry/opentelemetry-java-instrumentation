/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.SchemaUrls;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpServerExceptionRecorder {

  private static final Object NO_REQUEST = new Object();

  private final ExceptionRecorder<Object> exceptionRecorder;

  public static HttpServerExceptionRecorder create(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return new HttpServerExceptionRecorder(openTelemetry, instrumentationName);
  }

  private HttpServerExceptionRecorder(OpenTelemetry openTelemetry, String instrumentationName) {
    String instrumentationVersion =
        EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    exceptionRecorder =
        ExceptionRecorder.create(
            openTelemetry,
            instrumentationName,
            instrumentationVersion,
            SchemaUrls.V1_41_0,
            HttpServerExceptionRecorder::extractExceptionEvent);
  }

  public void recordException(Context context, Span span, Throwable throwable) {
    exceptionRecorder.recordException(context, span, throwable, NO_REQUEST, null);
  }

  private static void extractExceptionEvent(
      LogRecordBuilder logRecordBuilder, Context context, Object request) {
    logRecordBuilder.setEventName("http.server.request.exception").setSeverity(Severity.ERROR);
  }
}
