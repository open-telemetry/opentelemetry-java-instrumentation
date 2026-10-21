/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import graphql.GraphQLError;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GraphqlErrorRecorder {

  private final Logger logger;

  public GraphqlErrorRecorder(
      OpenTelemetry openTelemetry, String instrumentationName, @Nullable String schemaUrl) {
    LoggerBuilder builder = openTelemetry.getLogsBridge().loggerBuilder(instrumentationName);
    String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (version != null) {
      builder.setInstrumentationVersion(version);
    }
    if (schemaUrl != null) {
      builder.setSchemaUrl(schemaUrl);
    }
    logger = builder.build();
  }

  public void record(Context context, GraphQLError error) {
    Attributes attributes =
        Attributes.builder()
            .put(EXCEPTION_TYPE, String.valueOf(error.getErrorType()))
            .put(EXCEPTION_MESSAGE, error.getMessage())
            .build();
    if (emitExceptionAsSpanEvents()) {
      Span.fromContext(context).addEvent("exception", attributes);
    }
    if (emitExceptionAsLogs()) {
      logger
          .logRecordBuilder()
          .setContext(context)
          .setEventName("exception")
          .setSeverity(Severity.WARN)
          .setAllAttributes(attributes)
          .emit();
    }
  }
}
