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
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

class MongoInstrumenterFactory {

  private static final MongoAttributesExtractor attributesExtractor =
      new MongoAttributesExtractor();
  private static final NetClientAttributesExtractor<CommandStartedEvent, Void> netAttributesExtractor =
      new NetClientAttributesExtractor<>(new MongoNetAttributesAdapter());

  static Instrumenter<CommandStartedEvent, Void> createInstrumenter(
      OpenTelemetry openTelemetry, int maxNormalizedQueryLength) {

    MongoDbAttributesExtractor dbAttributesExtractor =
        new MongoDbAttributesExtractor(maxNormalizedQueryLength);
    SpanNameExtractor<CommandStartedEvent> spanNameExtractor =
        new MongoSpanNameExtractor(dbAttributesExtractor, attributesExtractor);

    return Instrumenter.<CommandStartedEvent, Void>builder(
            openTelemetry, "io.opentelemetry.mongo-3.1", spanNameExtractor)
        .addAttributesExtractor(dbAttributesExtractor)
        .addAttributesExtractor(netAttributesExtractor)
        .addAttributesExtractor(attributesExtractor)
        .newInstrumenter(SpanKindExtractor.alwaysClient());
  }
}
