/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import java.util.function.Function;

public final class ClickHouseInstrumenterFactory {

  @SuppressWarnings("deprecation") // to support old semconv
  public static Instrumenter<ClickHouseDbRequest, Void> createInstrumenter(
      String instrumenterName, Function<Throwable, String> errorCodeExtractor) {
    ClickHouseAttributesGetter dbAttributesGetter =
        new ClickHouseAttributesGetter(errorCodeExtractor);

    InstrumenterBuilder<ClickHouseDbRequest, Void> builder =
        Instrumenter.<ClickHouseDbRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                instrumenterName,
                DbClientSpanNameExtractor.createForMigration(dbAttributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.<ClickHouseDbRequest, Void>builder(dbAttributesGetter)
                    .setTableAttribute(null)
                    .build())
            .addOperationMetrics(DbClientMetrics.get());
    Experimental.setExceptionEventExtractor(builder, DbExceptionEventExtractors.client());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private ClickHouseInstrumenterFactory() {}
}
