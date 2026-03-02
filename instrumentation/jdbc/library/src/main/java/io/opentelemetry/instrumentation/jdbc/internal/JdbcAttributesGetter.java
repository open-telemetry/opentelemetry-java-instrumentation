/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcAttributesGetter implements SqlClientAttributesGetter<DbRequest, Void> {

  public static final JdbcAttributesGetter INSTANCE = new JdbcAttributesGetter();

  // Databases where double quotes are exclusively identifiers and cannot be string literals.
  private static final Set<String> DOUBLE_QUOTES_FOR_IDENTIFIERS_SYSTEMS =
      new HashSet<>(
          Arrays.asList(
              // "A string constant in SQL is an arbitrary sequence of characters
              // bounded by single quotes (')"
              // https://www.postgresql.org/docs/current/sql-syntax-lexical.html#SQL-SYNTAX-STRINGS
              "postgresql",
              // "Text, character, and string literals are always surrounded
              // by single quotation marks."
              // https://docs.oracle.com/en/database/oracle/oracle-database/23/sqlrf/Literals.html
              "oracle",
              // "A sequence of characters that starts and ends with a string delimiter,
              // which is an apostrophe (')"
              // https://www.ibm.com/docs/en/db2/12.1?topic=elements-constants
              "db2",
              // "Single quotation marks delimit character strings."
              // "Double quotation marks delimit special identifiers"
              // https://db.apache.org/derby/docs/10.17/ref/rrefsqlj28468.html
              "derby",
              // "names of objects are enclosed in double-quotes"
              // (double quotes are exclusively for identifiers; follows SQL standard strictly)
              // https://hsqldb.org/doc/2.0/guide/sqlgeneral-chapt.html
              "hsqldb",
              // <string_literal> ::= <single_quote>[<any_character>...]<single_quote>
              // <special_identifier> ::= <double_quotes><any_character>...<double_quotes>
              // https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/sql-notation-conventions
              "hanadb",
              // "String literals must be enclosed in single quotes.
              // Double quotes are not supported."
              // https://clickhouse.com/docs/en/sql-reference/syntax#string
              "clickhouse",
              // PostgreSQL-compatible fork, inherits PG string literal rules
              "polardb"));

  @Nullable
  @Override
  public String getDbSystemName(DbRequest request) {
    return request.getDbInfo().getSystem();
  }

  @Deprecated // to be removed in 3.0
  @Nullable
  @Override
  public String getUser(DbRequest request) {
    return request.getDbInfo().getUser();
  }

  @Nullable
  @Override
  public String getDbNamespace(DbRequest request) {
    return request.getDbInfo().getName();
  }

  @Deprecated // to be removed in 3.0
  @Nullable
  @Override
  public String getConnectionString(DbRequest request) {
    return request.getDbInfo().getShortUrl();
  }

  @Override
  public SqlDialect getSqlDialect(DbRequest request) {
    String system = request.getDbInfo().getSystem();
    if (system != null && DOUBLE_QUOTES_FOR_IDENTIFIERS_SYSTEMS.contains(system)) {
      return DOUBLE_QUOTES_ARE_IDENTIFIERS;
    }
    // default to treating double-quoted tokens as string literals for safety, ensuring that
    // potentially sensitive values are not captured
    return DOUBLE_QUOTES_ARE_STRING_LITERALS;
  }

  @Override
  public Collection<String> getRawQueryTexts(DbRequest request) {
    return request.getQueryTexts();
  }

  @Override
  public Long getDbOperationBatchSize(DbRequest request) {
    return request.getBatchSize();
  }

  @Nullable
  @Override
  public String getErrorType(
      DbRequest request, @Nullable Void response, @Nullable Throwable error) {
    if (error instanceof SQLException) {
      return Integer.toString(((SQLException) error).getErrorCode());
    }
    return null;
  }

  @Override
  public Map<String, String> getDbQueryParameters(DbRequest request) {
    return request.getPreparedStatementParameters();
  }

  @Override
  public boolean isParameterizedQuery(DbRequest request) {
    return request.isParameterizedQuery();
  }

  @Nullable
  @Override
  public String getServerAddress(DbRequest request) {
    return request.getDbInfo().getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbRequest request) {
    return request.getDbInfo().getPort();
  }
}
