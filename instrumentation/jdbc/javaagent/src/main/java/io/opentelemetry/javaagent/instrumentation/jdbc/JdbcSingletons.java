/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createDataSourceInstrumenter;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.sqlcommenter.SqlCommenterCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;
import javax.sql.DataSource;

public class JdbcSingletons {
  private static final Instrumenter<DbRequest, Void> statementInstrumenter;
  private static final Instrumenter<DbRequest, Void> transactionInstrumenter;
  private static final Instrumenter<DataSource, DbInfo> dataSourceInstrumenter =
      createDataSourceInstrumenter(GlobalOpenTelemetry.get(), true);
  private static final SqlCommenter sqlCommenter = configureSqlCommenter();
  private static final Cache<Class<?>, Boolean> wrapperClassCache = Cache.weak();
  public static final boolean CAPTURE_QUERY_PARAMETERS;

  static {
    AttributesExtractor<DbRequest, Void> servicePeerExtractor =
        ServicePeerAttributesExtractor.create(
            new JdbcAttributesGetter(), GlobalOpenTelemetry.get());

    CAPTURE_QUERY_PARAMETERS =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "jdbc")
            .getBoolean("capture_query_parameters/development", false);

    statementInstrumenter =
        JdbcInstrumenterFactory.createStatementInstrumenter(
            GlobalOpenTelemetry.get(),
            singletonList(servicePeerExtractor),
            true,
            DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "jdbc"),
            CAPTURE_QUERY_PARAMETERS);

    transactionInstrumenter =
        JdbcInstrumenterFactory.createTransactionInstrumenter(
            GlobalOpenTelemetry.get(),
            singletonList(servicePeerExtractor),
            DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "jdbc")
                .get("transaction/development")
                .getBoolean("enabled", false));
  }

  public static Instrumenter<DbRequest, Void> transactionInstrumenter() {
    return transactionInstrumenter;
  }

  public static Instrumenter<DbRequest, Void> statementInstrumenter() {
    return statementInstrumenter;
  }

  public static Instrumenter<DataSource, DbInfo> dataSourceInstrumenter() {
    return dataSourceInstrumenter;
  }

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
    } catch (SQLException | AbstractMethodError ignored) {
      // ignore
    }
    return false;
  }

  private static SqlCommenter configureSqlCommenter() {
    SqlCommenterBuilder builder = SqlCommenter.builder();
    builder.setEnabled(DbConfig.isSqlCommenterEnabled(GlobalOpenTelemetry.get(), "jdbc"));
    SqlCommenterCustomizerHolder.getCustomizer().customize(builder);
    return builder.build();
  }

  public static String processSql(Statement statement, String sql, boolean executed) {
    Connection connection;
    try {
      connection = statement.getConnection();
    } catch (SQLException ignored) {
      // connection was already closed
      return sql;
    }
    return processSql(connection, sql, executed);
  }

  public static String processSql(Connection connection, String sql, boolean executed) {
    return sqlCommenter.processQuery(connection, sql, executed);
  }

  private JdbcSingletons() {}
}
