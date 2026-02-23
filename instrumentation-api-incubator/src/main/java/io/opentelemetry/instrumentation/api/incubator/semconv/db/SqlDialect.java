/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import com.google.auto.value.AutoValue;

/** Describes SQL dialect options that affect how {@link SqlQuerySanitizer} processes queries. */
@AutoValue
public abstract class SqlDialect {

  /**
   * Dialect where double-quoted fragments are treated as string literals and therefore sanitized.
   * This is the safer default because it avoids leaking potentially sensitive string content.
   */
  public static final SqlDialect DOUBLE_QUOTES_ARE_STRING_LITERALS = builder().build();

  /**
   * Dialect where double-quoted fragments are treated as identifiers (e.g. column/table names) and
   * therefore left intact during sanitization. Use this for databases like PostgreSQL, Oracle, and
   * Db2 where double quotes always denote identifiers.
   */
  public static final SqlDialect DOUBLE_QUOTES_ARE_IDENTIFIERS =
      builder().setDoubleQuotesAreIdentifiers(true).build();

  SqlDialect() {}

  static Builder builder() {
    return new AutoValue_SqlDialect.Builder().setDoubleQuotesAreIdentifiers(false);
  }

  /**
   * Returns {@code true} if the dialect uses double quotes for identifiers, {@code false} if double
   * quotes are used for string literals.
   */
  abstract boolean doubleQuotesAreIdentifiers();

  @AutoValue.Builder
  abstract static class Builder {

    Builder() {}

    public abstract Builder setDoubleQuotesAreIdentifiers(boolean doubleQuotesAreIdentifiers);

    public abstract SqlDialect build();
  }
}
