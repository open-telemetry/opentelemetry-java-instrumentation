/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQueryAnalyzer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SqlDialectUtilTest {

  @ParameterizedTest
  @MethodSource("dialects")
  void resolvesDialect(String dbSystemName, SqlDialect expectedDialect) {
    assertThat(SqlDialectUtil.fromDbSystemName(dbSystemName)).isEqualTo(expectedDialect);
  }

  private static Stream<Arguments> dialects() {
    return Stream.of(
        argumentSet("PostgreSQL", "postgresql", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("Oracle", "oracle.db", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("DB2", "ibm.db2", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("Derby", "derby", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("HSQLDB", "hsqldb", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("SAP HANA", "sap.hana", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("ClickHouse", "clickhouse", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("PolarDB", "polardb", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("MySQL", "mysql", DOUBLE_QUOTES_ARE_STRING_LITERALS),
        argumentSet("SQL Server", "microsoft.sql_server", DOUBLE_QUOTES_ARE_STRING_LITERALS),
        argumentSet("unknown", "other_sql", DOUBLE_QUOTES_ARE_STRING_LITERALS),
        argumentSet("null", null, DOUBLE_QUOTES_ARE_STRING_LITERALS));
  }

  @ParameterizedTest
  @MethodSource("doubleQuotedQueries")
  void sanitizesDoubleQuotedQueries(
      String dbSystemName, String query, String expectedSanitizedQuery) {
    assertThat(
            SqlQueryAnalyzer.create(true)
                .analyze(query, SqlDialectUtil.fromDbSystemName(dbSystemName))
                .getQueryText())
        .isEqualTo(expectedSanitizedQuery);
  }

  private static Stream<Arguments> doubleQuotedQueries() {
    return Stream.of(
        argumentSet(
            "PostgreSQL identifier",
            "postgresql",
            "SELECT * FROM \"customers\"",
            "SELECT * FROM \"customers\""),
        argumentSet(
            "unknown system literal",
            "other_sql",
            "SELECT * FROM \"customers\"",
            "SELECT * FROM ?"));
  }
}
