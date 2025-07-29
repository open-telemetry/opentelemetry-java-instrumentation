/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;

public final class ClickHouseSingletons {

  private static final Instrumenter<ClickHouseDbRequest, Void> INSTRUMENTER;

  static {
    ClickHouseAttributesGetter dbAttributesGetter = new ClickHouseAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<ClickHouseDbRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.clickhouse-client-0.5",
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(
                ServerAttributesExtractor.create(new ClickHouseNetworkAttributesGetter()))
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<ClickHouseDbRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private ClickHouseSingletons() {}
}
