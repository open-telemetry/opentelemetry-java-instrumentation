/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.jdbc.internal.DataSourceInstrumenterFactory.createDataSourceInstrumenter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcNetAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import javax.sql.DataSource;

public final class JdbcSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jdbc";

  private static final Instrumenter<DbRequest, Void> STATEMENT_INSTRUMENTER;
  public static final Instrumenter<DataSource, DbInfo> DATASOURCE_INSTRUMENTER =
      createDataSourceInstrumenter(GlobalOpenTelemetry.get());

  static {
    JdbcAttributesGetter dbAttributesGetter = new JdbcAttributesGetter();
    JdbcNetAttributesGetter netAttributesGetter = new JdbcNetAttributesGetter();

    STATEMENT_INSTRUMENTER =
        Instrumenter.<DbRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(dbAttributesGetter)
                    .setStatementSanitizationEnabled(
                        InstrumentationConfig.get()
                            .getBoolean(
                                "otel.instrumentation.jdbc.statement-sanitizer.enabled",
                                CommonConfig.get().isStatementSanitizationEnabled()))
                    .build())
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    netAttributesGetter, CommonConfig.get().getPeerServiceMapping()))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<DbRequest, Void> statementInstrumenter() {
    return STATEMENT_INSTRUMENTER;
  }

  public static Instrumenter<DataSource, DbInfo> dataSourceInstrumenter() {
    return DATASOURCE_INSTRUMENTER;
  }

  private JdbcSingletons() {}
}
