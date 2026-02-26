/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

@SuppressWarnings("deprecation") // to support old semconv
public final class InfluxDbSingletons {

  private static final Instrumenter<InfluxDbRequest, Void> INSTRUMENTER;

  static {
    InfluxDbAttributesGetter dbAttributesGetter = new InfluxDbAttributesGetter();

    INSTRUMENTER =
        Instrumenter.builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.influxdb-2.4",
                DbClientSpanNameExtractor.createWithGenericOldSpanName(dbAttributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(dbAttributesGetter)
                    .setTableAttribute(null)
                    .build())
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<InfluxDbRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private InfluxDbSingletons() {}
}
