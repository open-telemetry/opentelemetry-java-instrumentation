/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

/** Enumeration of sql dialects that are handled differently by {@link SqlStatementSanitizer}. */
public enum SqlDialect {
  DEFAULT(false), // double quotes for string literals
  ANSI_QUOTES(true), // double quotes for identifiers, single quotes for string literals

  CASSANDRA(true),
  CLICKHOUSE(true),
  COUCHBASE(false),
  GEODE(true),
  INFLUXDB(true);

  private final boolean ansiQuotes;

  SqlDialect(boolean ansiQuotes) {
    this.ansiQuotes = ansiQuotes;
  }

  /**
   * Returns {@code true} if the dialect uses double quotes for identifiers (ANSI SQL standard),
   * {@code false} if double quotes are used for string literals.
   */
  boolean ansiQuotes() {
    return ansiQuotes;
  }
}
