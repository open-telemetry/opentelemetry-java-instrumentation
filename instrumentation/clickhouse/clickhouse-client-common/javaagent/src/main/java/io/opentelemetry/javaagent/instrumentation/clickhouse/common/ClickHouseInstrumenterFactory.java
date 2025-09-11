/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import java.util.function.Function;

public final class ClickHouseInstrumenterFactory {

  public static Instrumenter<ClickHouseDbRequest, Void> createInstrumenter(
      String instrumenterName, Function<Throwable, String> errorCodeExtractor) {
    ClickHouseAttributesGetter dbAttributesGetter =
        new ClickHouseAttributesGetter(errorCodeExtractor);

    return Instrumenter.<ClickHouseDbRequest, Void>builder(
            GlobalOpenTelemetry.get(),
            instrumenterName,
            DbClientSpanNameExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(
            ServerAttributesExtractor.create(new ClickHouseNetworkAttributesGetter()))
        .addOperationMetrics(DbClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private ClickHouseInstrumenterFactory() {}
}
