/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import javax.sql.DataSource;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcInstrumenterFactory {
  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.jdbc";
  private static final JdbcAttributesGetter dbAttributesGetter = new JdbcAttributesGetter();
  private static final JdbcNetworkAttributesGetter netAttributesGetter =
      new JdbcNetworkAttributesGetter();

  public static Instrumenter<DbRequest, Void> createStatementInstrumenter() {
    return createStatementInstrumenter(GlobalOpenTelemetry.get());
  }

  public static Instrumenter<DbRequest, Void> createStatementInstrumenter(
      OpenTelemetry openTelemetry) {
    return createStatementInstrumenter(
        openTelemetry,
        true,
        ConfigPropertiesUtil.getBoolean(
            "otel.instrumentation.common.db-statement-sanitizer.enabled", true));
  }

  public static Instrumenter<DbRequest, Void> createStatementInstrumenter(
      OpenTelemetry openTelemetry, boolean enabled, boolean statementSanitizationEnabled) {
    return Instrumenter.<DbRequest, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            DbClientSpanNameExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(
            SqlClientAttributesExtractor.builder(dbAttributesGetter)
                .setStatementSanitizationEnabled(statementSanitizationEnabled)
                .build())
        .addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter))
        .addOperationMetrics(DbClientMetrics.get())
        .setEnabled(enabled)
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<DataSource, DbInfo> createDataSourceInstrumenter(
      OpenTelemetry openTelemetry, boolean enabled) {
    DataSourceCodeAttributesGetter getter = DataSourceCodeAttributesGetter.INSTANCE;
    return Instrumenter.<DataSource, DbInfo>builder(
            openTelemetry, INSTRUMENTATION_NAME, CodeSpanNameExtractor.create(getter))
        .addAttributesExtractor(CodeAttributesExtractor.create(getter))
        .addAttributesExtractor(DataSourceDbAttributesExtractor.INSTANCE)
        .setEnabled(enabled)
        .buildInstrumenter();
  }

  private JdbcInstrumenterFactory() {}
}
