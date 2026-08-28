/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class SqlDialectUtil {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String IBM_DB2 = "ibm.db2";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String DERBY = "derby";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String HSQLDB = "hsqldb";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String SAP_HANA = "sap.hana";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String CLICKHOUSE = "clickhouse";
  // not a semantic convention value, produced by the JDBC PolarDB connection url parser
  private static final String POLARDB = "polardb";

  public static SqlDialect fromDbSystemName(@Nullable String dbSystemName) {
    if (dbSystemName == null) {
      return DOUBLE_QUOTES_ARE_STRING_LITERALS;
    }
    // databases where double quotes are exclusively identifiers and cannot be string literals
    switch (dbSystemName) {
      // "A string constant in SQL is an arbitrary sequence of characters
      // bounded by single quotes (')"
      // https://www.postgresql.org/docs/current/sql-syntax-lexical.html#SQL-SYNTAX-STRINGS
      case POSTGRESQL:
      // "Text, character, and string literals are always surrounded
      // by single quotation marks."
      // https://docs.oracle.com/en/database/oracle/oracle-database/23/sqlrf/Literals.html
      case ORACLE_DB:
      // "A sequence of characters that starts and ends with a string delimiter,
      // which is an apostrophe (')"
      // https://www.ibm.com/docs/en/db2/12.1?topic=elements-constants
      case IBM_DB2:
      // "Single quotation marks delimit character strings."
      // "Double quotation marks delimit special identifiers"
      // https://db.apache.org/derby/docs/10.17/ref/rrefsqlj28468.html
      case DERBY:
      // "names of objects are enclosed in double-quotes"
      // (double quotes are exclusively for identifiers; follows SQL standard strictly)
      // https://hsqldb.org/doc/2.0/guide/sqlgeneral-chapt.html
      case HSQLDB:
      // <string_literal> ::= <single_quote>[<any_character>...]<single_quote>
      // <special_identifier> ::= <double_quotes><any_character>...<double_quotes>
      // https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/sql-notation-conventions
      case SAP_HANA:
      // "String literals must be enclosed in single quotes.
      // Double quotes are not supported."
      // https://clickhouse.com/docs/en/sql-reference/syntax#string
      case CLICKHOUSE:
      // PostgreSQL-compatible fork, inherits PG string literal rules
      case POLARDB:
        return DOUBLE_QUOTES_ARE_IDENTIFIERS;
      default:
        // Favor sanitization when the database may interpret double-quoted tokens as literals.
        return DOUBLE_QUOTES_ARE_STRING_LITERALS;
    }
  }

  private SqlDialectUtil() {}
}
