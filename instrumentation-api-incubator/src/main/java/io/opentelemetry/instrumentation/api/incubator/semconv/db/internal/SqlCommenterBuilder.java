/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.sql.Connection;
import java.sql.Statement;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class SqlCommenterBuilder {
  private boolean enabled;
  private BiFunction<Object, Boolean, TextMapPropagator> propagator =
      (unused1, unused2) -> W3CTraceContextPropagator.getInstance();
  private Predicate<Object> prepend = unused -> false;

  SqlCommenterBuilder() {}

  /** Enable adding sqlcommenter comments to sql queries. Default is disabled. */
  @CanIgnoreReturnValue
  public SqlCommenterBuilder setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Prepend the sqlcommenter comment to the query instead of appending it. Default is to append.
   */
  @CanIgnoreReturnValue
  public SqlCommenterBuilder setPrepend(boolean prepend) {
    this.prepend = unused -> prepend;
    return this;
  }

  /**
   * Prepend the sqlcommenter comment to the query instead of appending it. Default is to append.
   *
   * @param prepend a predicate that receives the database connection. Connection may be a jdbc
   *     Connection, R2DBC Connection, or any other connection type used by the data access
   *     framework performing the operation.
   */
  @CanIgnoreReturnValue
  public SqlCommenterBuilder setPrepend(Predicate<Object> prepend) {
    this.prepend = prepend;
    return this;
  }

  /**
   * Set the propagator used to inject tracing context into sql comments. Default is W3C Trace
   * Context propagator.
   */
  @CanIgnoreReturnValue
  public SqlCommenterBuilder setPropagator(TextMapPropagator propagator) {
    this.propagator = (unused1, unused2) -> propagator;
    return this;
  }

  /**
   * Set the propagator used to inject tracing context into sql comments. Default is W3C Trace
   * Context propagator.
   *
   * @param propagator a function that receives the database connection and whether the query is
   *     executed only once or could be reused. Connection may be a jdbc Connection, R2DBC
   *     Connection, or any other connection type used by the data access framework performing the
   *     operation. If the second argument to the function is true, the query is executed only once
   *     (e.g. JDBC {@link Statement#execute(String)}). If false, the query could be reused (e.g.
   *     JDBC {@link Connection#prepareStatement(String)}).
   */
  @CanIgnoreReturnValue
  public SqlCommenterBuilder setPropagator(
      BiFunction<Object, Boolean, TextMapPropagator> propagator) {
    this.propagator = propagator;
    return this;
  }

  public SqlCommenter build() {
    return new SqlCommenter(enabled, propagator, prepend);
  }
}
