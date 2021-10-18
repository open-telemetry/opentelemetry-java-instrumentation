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

class MongoInstrumenterFactory {

  private static final MongoAttributesExtractor attributesExtractor =
      new MongoAttributesExtractor();
  private static final MongoNetAttributesExtractor netAttributesExtractor =
      new MongoNetAttributesExtractor();

  static Instrumenter<CommandStartedEvent, Void> createInstrumenter(
      OpenTelemetry openTelemetry, int maxNormalizedQueryLength) {

    MongoDbAttributesExtractor dbAttributesExtractor =
        new MongoDbAttributesExtractor(maxNormalizedQueryLength);
    SpanNameExtractor<CommandStartedEvent> spanNameExtractor =
        new MongoSpanNameExtractor(dbAttributesExtractor, attributesExtractor);

    return Instrumenter.<CommandStartedEvent, Void>newBuilder(
            openTelemetry, "io.opentelemetry.mongo-3.1", spanNameExtractor)
        .addAttributesExtractor(dbAttributesExtractor)
        .addAttributesExtractor(netAttributesExtractor)
        .addAttributesExtractor(attributesExtractor)
        .newInstrumenter(SpanKindExtractor.alwaysClient());
  }
}
