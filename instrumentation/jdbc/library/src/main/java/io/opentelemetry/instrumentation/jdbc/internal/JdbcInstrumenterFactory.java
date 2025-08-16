/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.util.List;
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

  public static boolean captureQueryParameters() {
    return ConfigPropertiesUtil.getBoolean(
        "otel.instrumentation.jdbc.experimental.capture-query-parameters", false);
  }

  public static Instrumenter<DbRequest, Void> createStatementInstrumenter(
      OpenTelemetry openTelemetry) {
    return createStatementInstrumenter(openTelemetry, captureQueryParameters());
  }

  static Instrumenter<DbRequest, Void> createStatementInstrumenter(
      OpenTelemetry openTelemetry, boolean captureQueryParameters) {
    return createStatementInstrumenter(
        openTelemetry,
        emptyList(),
        true,
        isStatementSanitizationEnabled(openTelemetry),
        captureQueryParameters);
  }

  private static boolean isStatementSanitizationEnabled(OpenTelemetry openTelemetry) {
    if (openTelemetry instanceof ExtendedOpenTelemetry) {
      ConfigProvider configProvider = ((ExtendedOpenTelemetry) openTelemetry).getConfigProvider();
      // we might want to pull the config bridge to instrumentation-api-incubator which we can
      // use here
      DeclarativeConfigProperties properties = configProvider.getInstrumentationConfig();
      if (properties == null) {
        properties = DeclarativeConfigProperties.empty();
      }
      return properties
          .getStructured("java", empty())
          .getStructured("common", empty())
          .getStructured("db_statement_sanitizer", empty())
          .getBoolean("enabled", true);
    }

    return ConfigPropertiesUtil.getBoolean(
        "otel.instrumentation.common.db-statement-sanitizer.enabled", true);
  }

  public static Instrumenter<DbRequest, Void> createStatementInstrumenter(
      OpenTelemetry openTelemetry,
      boolean enabled,
      boolean statementSanitizationEnabled,
      boolean captureQueryParameters) {
    return createStatementInstrumenter(
        openTelemetry, emptyList(), enabled, statementSanitizationEnabled, captureQueryParameters);
  }

  public static Instrumenter<DbRequest, Void> createStatementInstrumenter(
      OpenTelemetry openTelemetry,
      List<AttributesExtractor<DbRequest, Void>> extractors,
      boolean enabled,
      boolean statementSanitizationEnabled,
      boolean captureQueryParameters) {
    return Instrumenter.<DbRequest, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            DbClientSpanNameExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(
            SqlClientAttributesExtractor.builder(dbAttributesGetter)
                .setStatementSanitizationEnabled(statementSanitizationEnabled)
                .setCaptureQueryParameters(captureQueryParameters)
                .build())
        .addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter))
        .addAttributesExtractors(extractors)
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

  public static Instrumenter<DbRequest, Void> createTransactionInstrumenter(
      OpenTelemetry openTelemetry) {
    return createTransactionInstrumenter(
        openTelemetry,
        ConfigPropertiesUtil.getBoolean(
            "otel.instrumentation.jdbc.experimental.transaction.enabled", false));
  }

  public static Instrumenter<DbRequest, Void> createTransactionInstrumenter(
      OpenTelemetry openTelemetry, boolean enabled) {
    return createTransactionInstrumenter(openTelemetry, emptyList(), enabled);
  }

  public static Instrumenter<DbRequest, Void> createTransactionInstrumenter(
      OpenTelemetry openTelemetry,
      List<AttributesExtractor<DbRequest, Void>> extractors,
      boolean enabled) {
    return Instrumenter.<DbRequest, Void>builder(
            openTelemetry, INSTRUMENTATION_NAME, DbRequest::getOperation)
        .addAttributesExtractor(SqlClientAttributesExtractor.builder(dbAttributesGetter).build())
        .addAttributesExtractor(TransactionAttributeExtractor.INSTANCE)
        .addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter))
        .addAttributesExtractors(extractors)
        .setEnabled(enabled)
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private JdbcInstrumenterFactory() {}
}
