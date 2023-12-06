/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public final class GeodeSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.geode-1.4";

  private static final Instrumenter<GeodeRequest, Void> INSTRUMENTER;

  static {
    GeodeDbAttributeGetter dbClientAttributeGetter = new GeodeDbAttributeGetter();

    INSTRUMENTER =
        Instrumenter.<GeodeRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbClientAttributeGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributeGetter))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<GeodeRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private GeodeSingletons() {}
}
