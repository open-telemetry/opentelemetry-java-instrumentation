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
   * Dialect where double-quoted fragments are treated as string literals and therefore sanitized
   * (replaced with {@code ?}).
   *
   * <p>This is a safer choice if you don't know how the database interprets double-quoted tokens.
   * If the database actually uses double quotes for identifiers, the downside is that identifier
   * names are replaced with {@code ?}, reducing diagnostic clarity. By contrast, choosing {@link
   * #DOUBLE_QUOTES_ARE_IDENTIFIERS} incorrectly would fail to sanitize sensitive string-literal
   * values.
   */
  public static final SqlDialect DOUBLE_QUOTES_ARE_STRING_LITERALS = builder().build();

  /**
   * Dialect where double-quoted fragments are treated as identifiers (e.g. column/table names) and
   * therefore left intact during sanitization. Use this only for databases where double quotes
   * always denote identifiers.
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

    abstract Builder setDoubleQuotesAreIdentifiers(boolean doubleQuotesAreIdentifiers);

    abstract SqlDialect build();
  }
}
