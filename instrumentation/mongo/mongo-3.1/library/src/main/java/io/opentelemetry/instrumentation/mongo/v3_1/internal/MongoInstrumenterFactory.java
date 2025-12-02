/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MongoInstrumenterFactory {

  public static final int DEFAULT_MAX_NORMALIZED_QUERY_LENGTH = 32 * 1024;

  private static final MongoAttributesExtractor attributesExtractor =
      new MongoAttributesExtractor();

  public static Instrumenter<CommandStartedEvent, Void> createInstrumenter(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean statementSanitizationEnabled) {
    return createInstrumenter(
        openTelemetry,
        instrumentationName,
        statementSanitizationEnabled,
        DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);
  }

  public static Instrumenter<CommandStartedEvent, Void> createInstrumenter(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean statementSanitizationEnabled,
      int maxNormalizedQueryLength) {

    MongoDbAttributesGetter dbAttributesGetter =
        new MongoDbAttributesGetter(statementSanitizationEnabled, maxNormalizedQueryLength);
    SpanNameExtractor<CommandStartedEvent> spanNameExtractor =
        new MongoSpanNameExtractor(dbAttributesGetter, attributesExtractor);

    return Instrumenter.<CommandStartedEvent, Void>builder(
            openTelemetry, instrumentationName, spanNameExtractor)
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(
            ServerAttributesExtractor.create(new MongoNetworkAttributesGetter()))
        .addAttributesExtractor(attributesExtractor)
        .addOperationMetrics(DbClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private MongoInstrumenterFactory() {}
}
