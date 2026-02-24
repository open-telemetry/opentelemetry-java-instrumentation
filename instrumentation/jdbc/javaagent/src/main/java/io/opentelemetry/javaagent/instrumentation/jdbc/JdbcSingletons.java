/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createDataSourceInstrumenter;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.DbResponse;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcTransactionAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.sqlcommenter.SqlCommenterCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;
import javax.sql.DataSource;

public final class JdbcSingletons {
  private static final Instrumenter<DbRequest, DbResponse> STATEMENT_INSTRUMENTER;
  private static final Instrumenter<DbRequest, Void> TRANSACTION_INSTRUMENTER;
  public static final Instrumenter<DataSource, DbInfo> DATASOURCE_INSTRUMENTER =
      createDataSourceInstrumenter(GlobalOpenTelemetry.get(), true);
  private static final SqlCommenter SQL_COMMENTER = configureSqlCommenter();
  public static final boolean CAPTURE_QUERY_PARAMETERS;
  public static final boolean CAPTURE_ROW_COUNT;
  public static final long ROW_COUNT_LIMIT;

  static {
    AttributesExtractor<DbRequest, DbResponse> servicePeerExtractor =
        ServicePeerAttributesExtractor.create(
            JdbcAttributesGetter.INSTANCE, GlobalOpenTelemetry.get());
    AttributesExtractor<DbRequest, Void> transactionServicePeerExtractor =
        ServicePeerAttributesExtractor.create(
            JdbcTransactionAttributesGetter.INSTANCE, GlobalOpenTelemetry.get());

    CAPTURE_QUERY_PARAMETERS =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "jdbc")
            .getBoolean("capture_query_parameters/development", false);

    CAPTURE_ROW_COUNT = JdbcInstrumenterFactory.captureRowCount(GlobalOpenTelemetry.get());
    ROW_COUNT_LIMIT = JdbcInstrumenterFactory.rowCountLimit(GlobalOpenTelemetry.get());

    STATEMENT_INSTRUMENTER =
        JdbcInstrumenterFactory.createStatementInstrumenter(
            GlobalOpenTelemetry.get(),
            singletonList(servicePeerExtractor),
            true,
            DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "jdbc")
                .get("statement_sanitizer")
                .getBoolean("enabled", AgentCommonConfig.get().isQuerySanitizationEnabled()),
            CAPTURE_QUERY_PARAMETERS);

    TRANSACTION_INSTRUMENTER =
        JdbcInstrumenterFactory.createTransactionInstrumenter(
            GlobalOpenTelemetry.get(),
            singletonList(transactionServicePeerExtractor),
            DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "jdbc")
                .get("transaction/development")
                .getBoolean("enabled", false));
  }

  public static Instrumenter<DbRequest, Void> transactionInstrumenter() {
    return TRANSACTION_INSTRUMENTER;
  }

  public static Instrumenter<DbRequest, DbResponse> statementInstrumenter() {
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
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "jdbc")
            .get("sqlcommenter/development")
            .getBoolean("enabled", AgentCommonConfig.get().isSqlCommenterEnabled()));
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
