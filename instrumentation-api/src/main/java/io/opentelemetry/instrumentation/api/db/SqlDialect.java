/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

/** Enumeration of sql dialects that are handled differently by {@link SqlStatementSanitizer}. */
public enum SqlDialect {
  DEFAULT,
  // couchbase uses double quotes for string literals
  COUCHBASE
}
