/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.Nullable;

/** Describes SQL dialect options that affect how {@link SqlQueryAnalyzer} processes queries. */
@AutoValue
public abstract class SqlDialect {

  /**
   * Dialect for queries whose database is unknown. Double-quoted fragments are treated as string
   * literals and sanitized (replaced with {@code ?}).
   */
  public static final SqlDialect UNKNOWN = builder().build();

  private static final SqlDialect IDENTIFIERS =
      builder().setDoubleQuotesAreIdentifiers(true).build();

  /**
   * Dialect where double-quoted fragments are treated as string literals and therefore sanitized
   * (replaced with {@code ?}).
   *
   * @deprecated Use {@link #UNKNOWN} instead. May be removed in the next minor release.
   */
  @Deprecated public static final SqlDialect DOUBLE_QUOTES_ARE_STRING_LITERALS = UNKNOWN;

  /**
   * Dialect where double-quoted fragments are treated as identifiers (e.g. column/table names) and
   * therefore left intact during sanitization. Use this only for databases where double quotes
   * always denote identifiers.
   *
   * @deprecated Use {@link #forDbSystemName(String)} or {@link #builder()} instead. May be removed
   *     in the next minor release.
   */
  @Deprecated public static final SqlDialect DOUBLE_QUOTES_ARE_IDENTIFIERS = IDENTIFIERS;

  /**
   * Returns the dialect for the given {@code db.system.name} value, or {@link #UNKNOWN} if the
   * database is {@code null} or not recognized.
   */
  public static SqlDialect forDbSystemName(@Nullable String dbSystemName) {
    if (dbSystemName == null) {
      return UNKNOWN;
    }
    switch (dbSystemName) {
      // "A string constant in SQL is an arbitrary sequence of characters
      // bounded by single quotes (')"
      // https://www.postgresql.org/docs/current/sql-syntax-lexical.html#SQL-SYNTAX-STRINGS
      case "postgresql":
      // "Text, character, and string literals are always surrounded
      // by single quotation marks."
      // https://docs.oracle.com/en/database/oracle/oracle-database/23/sqlrf/Literals.html
      case "oracle.db":
      // "A sequence of characters that starts and ends with a string delimiter,
      // which is an apostrophe (')"
      // https://www.ibm.com/docs/en/db2/12.1?topic=elements-constants
      case "ibm.db2":
      // "Single quotation marks delimit character strings."
      // "Double quotation marks delimit special identifiers"
      // https://db.apache.org/derby/docs/10.17/ref/rrefsqlj28468.html
      case "derby":
      // "names of objects are enclosed in double-quotes"
      // (double quotes are exclusively for identifiers; follows SQL standard strictly)
      // https://hsqldb.org/doc/2.0/guide/sqlgeneral-chapt.html
      case "hsqldb":
      // <string_literal> ::= <single_quote>[<any_character>...]<single_quote>
      // <special_identifier> ::= <double_quotes><any_character>...<double_quotes>
      // https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/sql-notation-conventions
      case "sap.hana":
      // "String literals must be enclosed in single quotes.
      // Double quotes are not supported."
      // https://clickhouse.com/docs/en/sql-reference/syntax#string
      case "clickhouse":
      // PostgreSQL-compatible fork, inherits PG string literal rules
      // (not a semantic convention value, produced by the JDBC PolarDB connection url parser)
      case "polardb":
      // "A string constant is an arbitrary sequence of characters enclosed by single-quote(')."
      // https://cassandra.apache.org/doc/stable/cassandra/developing/cql/definitions.html#constants
      case "cassandra":
      // "String literals must be surrounded by single quotes."
      // https://docs.influxdata.com/influxdb/v2/reference/syntax/influxql/spec/#strings
      case "influxdb":
        return IDENTIFIERS;
      default:
        return UNKNOWN;
    }
  }

  /**
   * Returns a builder for explicit dialect options. By default, double-quoted fragments are treated
   * as string literals, as with {@link #UNKNOWN}.
   */
  public static Builder builder() {
    return new AutoValue_SqlDialect.Builder().setDoubleQuotesAreIdentifiers(false);
  }

  SqlDialect() {}

  /**
   * Returns {@code true} if the dialect uses double quotes for identifiers, {@code false} if double
   * quotes are used for string literals.
   */
  abstract boolean doubleQuotesAreIdentifiers();

  /** Builder for SQL dialect options. */
  @AutoValue.Builder
  public abstract static class Builder {

    Builder() {}

    /**
     * Sets whether double-quoted fragments are identifiers and preserved, rather than string
     * literals that are sanitized. Use {@code true} only when double quotes always denote
     * identifiers.
     */
    @CanIgnoreReturnValue
    public abstract Builder setDoubleQuotesAreIdentifiers(boolean doubleQuotesAreIdentifiers);

    public abstract SqlDialect build();
  }
}
