/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;

class MongoInstrumenterFactory {

  private static final MongoAttributesExtractor attributesExtractor =
      new MongoAttributesExtractor();

  static Instrumenter<CommandStartedEvent, Void> createInstrumenter(
      OpenTelemetry openTelemetry,
      boolean statementSanitizationEnabled,
      int maxNormalizedQueryLength) {

    MongoDbAttributesGetter dbAttributesGetter =
        new MongoDbAttributesGetter(statementSanitizationEnabled, maxNormalizedQueryLength);
    SpanNameExtractor<CommandStartedEvent> spanNameExtractor =
        new MongoSpanNameExtractor(dbAttributesGetter, attributesExtractor);

    return Instrumenter.<CommandStartedEvent, Void>builder(
            openTelemetry, "io.opentelemetry.mongo-3.1", spanNameExtractor)
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(
            ServerAttributesExtractor.create(new MongoNetworkAttributesGetter()))
        .addAttributesExtractor(attributesExtractor)
        .addOperationMetrics(DbClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private MongoInstrumenterFactory() {}
}
