/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class SqlCommenter {
  private final boolean enabled;
  private final BiFunction<Object, Boolean, TextMapPropagator> propagator;
  private final Predicate<Object> prepend;

  SqlCommenter(
      boolean enabled,
      BiFunction<Object, Boolean, TextMapPropagator> propagator,
      Predicate<Object> prepend) {
    this.enabled = enabled;
    this.propagator = propagator;
    this.prepend = prepend;
  }

  public static SqlCommenterBuilder builder() {
    return new SqlCommenterBuilder();
  }

  public static SqlCommenter noop() {
    return builder().build();
  }

  /**
   * Augments the given SQL query with comment containing tracing context.
   *
   * @param connection connection object, e.g. JDBC connection or R2DBC connection, that is used to
   *     execute the query
   * @param sql original query
   * @param executed whether the query is immediately executed after being processed, e.g. {@link
   *     java.sql.Statement#execute(String)}, or may be executed later, e.g. {@link
   *     java.sql.Connection#prepareStatement(String)}
   * @return modified query
   */
  public String processQuery(Object connection, String sql, boolean executed) {
    if (!enabled) {
      return sql;
    }

    return SqlCommenterUtil.processQuery(
        sql, propagator.apply(connection, executed), prepend.test(connection));
  }

  public boolean isEnabled() {
    return enabled;
  }
}
