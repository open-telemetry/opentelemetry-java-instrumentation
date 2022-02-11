/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;

public final class GeodeSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.geode-1.4";

  private static final Instrumenter<GeodeRequest, Void> INSTRUMENTER;

  static {
    GeodeDbAttributesGetter dbClientAttributesGetter = new GeodeDbAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<GeodeRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbClientAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<GeodeRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private GeodeSingletons() {}
}
