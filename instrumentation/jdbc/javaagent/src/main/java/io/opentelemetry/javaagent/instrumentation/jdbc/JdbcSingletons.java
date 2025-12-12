/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createDataSourceInstrumenter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.internal.sqlcommenter.SqlCommenterCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;
import java.util.Collections;
import javax.sql.DataSource;

public final class JdbcSingletons {
  private static final Instrumenter<DbRequest, Void> STATEMENT_INSTRUMENTER;
  private static final Instrumenter<DbRequest, Void> TRANSACTION_INSTRUMENTER;
  public static final Instrumenter<DataSource, DbInfo> DATASOURCE_INSTRUMENTER =
      createDataSourceInstrumenter(GlobalOpenTelemetry.get(), true);
  private static final SqlCommenter SQL_COMMENTER = configureSqlCommenter();
  public static final boolean CAPTURE_QUERY_PARAMETERS;

  static {
    AttributesExtractor<DbRequest, Void> peerServiceExtractor =
        PeerServiceAttributesExtractor.create(
            JdbcAttributesGetter.INSTANCE, AgentCommonConfig.get().getPeerServiceResolver());

    CAPTURE_QUERY_PARAMETERS =
        AgentInstrumentationConfig.get()
            .getBoolean("otel.instrumentation.jdbc.experimental.capture-query-parameters", false);

    STATEMENT_INSTRUMENTER =
        JdbcInstrumenterFactory.createStatementInstrumenter(
            GlobalOpenTelemetry.get(),
            Collections.singletonList(peerServiceExtractor),
            true,
            AgentInstrumentationConfig.get()
                .getBoolean(
                    "otel.instrumentation.jdbc.statement-sanitizer.enabled",
                    AgentCommonConfig.get().isStatementSanitizationEnabled()),
            AgentInstrumentationConfig.get()
                .getBoolean(
                    "otel.instrumentation.jdbc.statement-sanitizer.ansi-quotes",
                    AgentCommonConfig.get().isStatementSanitizationAnsiQuotes()),
            CAPTURE_QUERY_PARAMETERS);

    TRANSACTION_INSTRUMENTER =
        JdbcInstrumenterFactory.createTransactionInstrumenter(
            GlobalOpenTelemetry.get(),
            Collections.singletonList(peerServiceExtractor),
            AgentInstrumentationConfig.get()
                .getBoolean("otel.instrumentation.jdbc.experimental.transaction.enabled", false));
  }

  public static Instrumenter<DbRequest, Void> transactionInstrumenter() {
    return TRANSACTION_INSTRUMENTER;
  }

  public static Instrumenter<DbRequest, Void> statementInstrumenter() {
    return STATEMENT_INSTRUMENTER;
  }

  public static Instrumenter<DataSource, DbInfo> dataSourceInstrumenter() {
    return DATASOURCE_INSTRUMENTER;
  }

  private static final Cache<Class<?>, Boolean> wrapperClassCache = Cache.weak();

  /**
   * Returns true if the given object is a wrapper and shouldn't be instrumented. We'll instrument
   * the underlying object called by the wrapper instead.
   */
  public static <T extends Wrapper> boolean isWrapper(T object, Class<T> clazz) {
    return wrapperClassCache.computeIfAbsent(
        object.getClass(), key -> isWrapperInternal(object, clazz));
  }

  private static <T extends Wrapper> boolean isWrapperInternal(T object, Class<T> clazz) {
    try {
      // we are dealing with a wrapper when the object unwraps to a different instance
      if (object.isWrapperFor(clazz)) {
        T unwrapped = object.unwrap(clazz);
        if (object != unwrapped) {
          return true;
        }
      }
    } catch (SQLException | AbstractMethodError e) {
      // ignore
    }
    return false;
  }

  private static SqlCommenter configureSqlCommenter() {
    SqlCommenterBuilder builder = SqlCommenter.builder();
    builder.setEnabled(
        AgentInstrumentationConfig.get()
            .getBoolean(
                "otel.instrumentation.jdbc.experimental.sqlcommenter.enabled",
                AgentCommonConfig.get().isSqlCommenterEnabled()));
    SqlCommenterCustomizerHolder.getCustomizer().customize(builder);
    return builder.build();
  }

  public static String processSql(Statement statement, String sql, boolean executed) {
    Connection connection;
    try {
      connection = statement.getConnection();
    } catch (SQLException exception) {
      // connection was already closed
      return sql;
    }
    return processSql(connection, sql, executed);
  }

  public static String processSql(Connection connection, String sql, boolean executed) {
    return SQL_COMMENTER.processQuery(connection, sql, executed);
  }

  private JdbcSingletons() {}
}
