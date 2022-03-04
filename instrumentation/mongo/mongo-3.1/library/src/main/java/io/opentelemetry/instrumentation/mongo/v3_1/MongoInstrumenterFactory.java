/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

class MongoInstrumenterFactory {

  private static final MongoAttributesExtractor attributesExtractor =
      new MongoAttributesExtractor();
  private static final NetClientAttributesExtractor<CommandStartedEvent, Void>
      netAttributesExtractor = NetClientAttributesExtractor.create(new MongoNetAttributesGetter());

  static Instrumenter<CommandStartedEvent, Void> createInstrumenter(
      OpenTelemetry openTelemetry, int maxNormalizedQueryLength) {

    MongoDbAttributesGetter dbAttributesGetter =
        new MongoDbAttributesGetter(maxNormalizedQueryLength);
    SpanNameExtractor<CommandStartedEvent> spanNameExtractor =
        new MongoSpanNameExtractor(dbAttributesGetter, attributesExtractor);

    return Instrumenter.<CommandStartedEvent, Void>builder(
            openTelemetry, "io.opentelemetry.mongo-3.1", spanNameExtractor)
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(netAttributesExtractor)
        .addAttributesExtractor(attributesExtractor)
        .newInstrumenter(SpanKindExtractor.alwaysClient());
  }
}
