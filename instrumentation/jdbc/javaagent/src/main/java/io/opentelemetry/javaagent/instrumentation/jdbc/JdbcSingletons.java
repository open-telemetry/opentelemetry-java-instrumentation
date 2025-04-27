/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createDataSourceInstrumenter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcStatementAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcTransactionAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.StatementNetworkAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.TransactionNetworkAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.TransactionRequest;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import javax.sql.DataSource;

public final class JdbcSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jdbc";

  private static final Instrumenter<DbRequest, Void> STATEMENT_INSTRUMENTER;
  private static final Instrumenter<TransactionRequest, Void> TRANSACTION_INSTRUMENTER;
  public static final Instrumenter<DataSource, DbInfo> DATASOURCE_INSTRUMENTER =
      createDataSourceInstrumenter(GlobalOpenTelemetry.get(), true);

  static {
    JdbcStatementAttributesGetter statementAttributesGetter = new JdbcStatementAttributesGetter();
    JdbcTransactionAttributesGetter transactionAttributesGetter =
        new JdbcTransactionAttributesGetter();
    StatementNetworkAttributesGetter statementNetAttributesGetter =
        new StatementNetworkAttributesGetter();
    TransactionNetworkAttributesGetter transactionNetAttributesGetter =
        new TransactionNetworkAttributesGetter();

    STATEMENT_INSTRUMENTER =
        Instrumenter.<DbRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(statementAttributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(statementAttributesGetter)
                    .setStatementSanitizationEnabled(
                        AgentInstrumentationConfig.get()
                            .getBoolean(
                                "otel.instrumentation.jdbc.statement-sanitizer.enabled",
                                AgentCommonConfig.get().isStatementSanitizationEnabled()))
                    .build())
            .addAttributesExtractor(ServerAttributesExtractor.create(statementNetAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    statementNetAttributesGetter, AgentCommonConfig.get().getPeerServiceResolver()))
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    TRANSACTION_INSTRUMENTER =
        Instrumenter.<TransactionRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, TransactionRequest::spanName)
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(transactionAttributesGetter).build())
            .addAttributesExtractor(
                ServerAttributesExtractor.create(transactionNetAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    transactionNetAttributesGetter,
                    AgentCommonConfig.get().getPeerServiceResolver()))
            .addOperationMetrics(DbClientMetrics.get())
            .setEnabled(
                AgentInstrumentationConfig.get()
                    .getBoolean(
                        "otel.instrumentation.jdbc.experimental.transaction.enabled", false))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<TransactionRequest, Void> transactionInstrumenter() {
    return TRANSACTION_INSTRUMENTER;
  }

  public static Instrumenter<DbRequest, Void> statementInstrumenter() {
    return STATEMENT_INSTRUMENTER;
  }

  public static Instrumenter<DataSource, DbInfo> dataSourceInstrumenter() {
    return DATASOURCE_INSTRUMENTER;
  }

  private JdbcSingletons() {}
}
