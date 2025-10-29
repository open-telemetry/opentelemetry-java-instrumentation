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

  public String processQuery(Object connection, String sql, boolean safe) {
    if (!enabled) {
      return sql;
    }

    return SqlCommenterUtil.processQuery(
        sql, propagator.apply(connection, safe), prepend.test(connection));
  }

  public boolean isEnabled() {
    return enabled;
  }
}
