/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.datasource.internal;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenterBuilder;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile Function<JdbcTelemetryBuilder, SqlCommenterBuilder> sqlCommenterBuilder;

  /**
   * Sets whether to augment sql query with comment containing the tracing information. See <a
   * href="https://google.github.io/sqlcommenter/">sqlcommenter</a> for more info.
   *
   * <p>WARNING: augmenting queries with tracing context will make query texts unique, which may
   * have adverse impact on database performance. Consult with database experts before enabling. If
   * this is an issue, consider replacing the default W3C Trace Context propagator with a propagator
   * that only adds static values, such as service name.
   *
   * <p>WARNING: for prepared statements query is modified when the statement is created, this means
   * that the inserted tracing context could be wrong if the prepared statement is reused. Consider
   * replacing the default W3C Trace Context propagator with a propagator that only adds static
   * values, such as service name.
   */
  public static void setEnableSqlCommenter(
      JdbcTelemetryBuilder builder, boolean sqlCommenterEnabled) {
    if (sqlCommenterBuilder != null) {
      sqlCommenterBuilder.apply(builder).setEnabled(sqlCommenterEnabled);
    }
  }

  /**
   * Set the propagator used to inject tracing context into sql comments. By default, W3C Trace
   * Context propagator is used.
   */
  public static void setSqlCommenterPropagator(
      JdbcTelemetryBuilder builder, TextMapPropagator propagator) {
    if (sqlCommenterBuilder != null) {
      sqlCommenterBuilder.apply(builder).setPropagator(propagator);
    }
  }

  /**
   * Sets whether to prepend the sql comment to the query instead of appending it. Default is to
   * append. This is useful for preserving the comment on databases that truncate long queries in
   * their diagnostic output.
   */
  public static void setSqlCommenterPrepend(
      JdbcTelemetryBuilder builder, boolean sqlCommenterPrepend) {
    if (sqlCommenterBuilder != null) {
      sqlCommenterBuilder.apply(builder).setPrepend(sqlCommenterPrepend);
    }
  }

  public static void internalSetSqlCommenterBuilder(
      Function<JdbcTelemetryBuilder, SqlCommenterBuilder> sqlCommenterBuilder) {
    Experimental.sqlCommenterBuilder = sqlCommenterBuilder;
  }

  private Experimental() {}
}
