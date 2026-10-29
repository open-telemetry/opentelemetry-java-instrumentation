/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.function.Function;

public class ClickHouseInstrumenterFactory {

  public static Instrumenter<ClickHouseDbRequest, Void> createInstrumenter(
      String instrumenterName, Function<Throwable, String> errorCodeExtractor) {
    return createInstrumenter(instrumenterName, errorCodeExtractor, errorCodeExtractor);
  }

  public static Instrumenter<ClickHouseDbRequest, Void> createInstrumenter(
      String instrumenterName,
      Function<Throwable, String> errorCodeExtractor,
      Function<Throwable, String> responseStatusExtractor) {
    ClickHouseAttributesGetter dbAttributesGetter =
        new ClickHouseAttributesGetter(errorCodeExtractor, responseStatusExtractor);

    InstrumenterBuilder<ClickHouseDbRequest, Void> builder =
        Instrumenter.<ClickHouseDbRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                instrumenterName,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(dbAttributesGetter)
                    .setQuerySanitizationEnabled(
                        DbConfig.isQuerySanitizationEnabled(
                            GlobalOpenTelemetry.get(), "clickhouse"))
                    .build())
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private ClickHouseInstrumenterFactory() {}
}
