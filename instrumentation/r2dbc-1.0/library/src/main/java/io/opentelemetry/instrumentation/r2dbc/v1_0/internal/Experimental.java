/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenterBuilder;
import io.opentelemetry.instrumentation.r2dbc.v1_0.R2dbcTelemetryBuilder;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile Function<R2dbcTelemetryBuilder, SqlCommenterBuilder> sqlCommenterBuilder;

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
      R2dbcTelemetryBuilder builder, boolean sqlCommenterEnabled) {
    if (sqlCommenterBuilder != null) {
      sqlCommenterBuilder.apply(builder).setEnabled(sqlCommenterEnabled);
    }
  }

  /**
   * Set the propagator used to inject tracing context into sql comments. By default, W3C Trace
   * Context propagator is used.
   */
  public static void setSqlCommenterPropagator(
      R2dbcTelemetryBuilder builder, TextMapPropagator propagator) {
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
      R2dbcTelemetryBuilder builder, boolean sqlCommenterPrepend) {
    if (sqlCommenterBuilder != null) {
      sqlCommenterBuilder.apply(builder).setPrepend(sqlCommenterPrepend);
    }
  }

  /**
   * Customize the configuration of {@link SqlCommenterBuilder} used in {@link
   * R2dbcTelemetryBuilder}.
   */
  public static void customizeSqlCommenter(
      R2dbcTelemetryBuilder builder, Consumer<SqlCommenterBuilder> customizer) {
    if (sqlCommenterBuilder != null) {
      customizer.accept(sqlCommenterBuilder.apply(builder));
    }
  }

  public static void internalSetSqlCommenterBuilder(
      Function<R2dbcTelemetryBuilder, SqlCommenterBuilder> sqlCommenterBuilder) {
    Experimental.sqlCommenterBuilder = sqlCommenterBuilder;
  }

  private Experimental() {}
}
