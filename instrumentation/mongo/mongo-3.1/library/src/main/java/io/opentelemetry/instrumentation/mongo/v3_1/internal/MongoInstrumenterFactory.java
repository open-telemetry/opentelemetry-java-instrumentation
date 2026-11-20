/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MongoInstrumenterFactory {

  static final int DEFAULT_MAX_NORMALIZED_QUERY_LENGTH = 32 * 1024;

  public static Instrumenter<CommandStartedEvent, CommandSucceededEvent> createInstrumenter(
      OpenTelemetry openTelemetry, String instrumentationName, boolean querySanitizationEnabled) {
    return createInstrumenter(
        openTelemetry,
        instrumentationName,
        querySanitizationEnabled,
        DEFAULT_MAX_NORMALIZED_QUERY_LENGTH,
        null);
  }

  public static Instrumenter<CommandStartedEvent, CommandSucceededEvent> createInstrumenter(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean querySanitizationEnabled,
      MongoConnectionPeerResolver connectionPeerResolver) {
    return createInstrumenter(
        openTelemetry,
        instrumentationName,
        querySanitizationEnabled,
        DEFAULT_MAX_NORMALIZED_QUERY_LENGTH,
        connectionPeerResolver);
  }

  public static Instrumenter<CommandStartedEvent, CommandSucceededEvent> createInstrumenter(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean querySanitizationEnabled,
      int maxNormalizedQueryLength) {
    return createInstrumenter(
        openTelemetry,
        instrumentationName,
        querySanitizationEnabled,
        maxNormalizedQueryLength,
        null);
  }

  private static Instrumenter<CommandStartedEvent, CommandSucceededEvent> createInstrumenter(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean querySanitizationEnabled,
      int maxNormalizedQueryLength,
      @Nullable MongoConnectionPeerResolver connectionPeerResolver) {

    MongoDbAttributesGetter dbAttributesGetter =
        new MongoDbAttributesGetter(
            querySanitizationEnabled, maxNormalizedQueryLength, connectionPeerResolver);
    InstrumenterBuilder<CommandStartedEvent, CommandSucceededEvent> builder =
        Instrumenter.<CommandStartedEvent, CommandSucceededEvent>builder(
                openTelemetry,
                instrumentationName,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .setSpanStatusExtractor(
                (status, request, response, error) -> {
                  if (dbAttributesGetter.getErrorType(request, response, error) != null) {
                    status.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.getDefault().extract(status, request, response, error);
                  }
                })
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private MongoInstrumenterFactory() {}
}
