/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;

public final class InfluxDbSingletons {

  private static final Instrumenter<InfluxDbRequest, Void> INSTRUMENTER;

  static {
    InfluxDbAttributesGetter dbAttributesGetter = new InfluxDbAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<InfluxDbRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.influxdb-2.4",
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(
                ServerAttributesExtractor.create(new InfluxDbNetworkAttributesGetter()))
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<InfluxDbRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private InfluxDbSingletons() {}
}
