/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.DERBY;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.CLICKHOUSE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.IBM_DB2;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ORACLE_DB;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.SAP_HANA;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JdbcAttributesGetterTest {

  private static final JdbcAttributesGetter attributesGetter = new JdbcAttributesGetter();
  private static final String POLARDB = "polardb";

  private static Stream<String> identifierDialectDbSystemNames() {
    return Stream.of(POSTGRESQL, ORACLE_DB, IBM_DB2, DERBY, HSQLDB, SAP_HANA, CLICKHOUSE, POLARDB);
  }

  private static Stream<String> stringLiteralDialectDbSystemNames() {
    return Stream.of(
        // "A string is a sequence of bytes or characters, enclosed within either single quote
        // (') or double quote (") characters."
        // https://dev.mysql.com/doc/refman/8.0/en/string-literals.html
        MYSQL,
        // "When SET QUOTED_IDENTIFIER is OFF, ... Literals can be delimited by either single or
        // double quotation marks."
        // https://learn.microsoft.com/en-us/sql/t-sql/statements/set-quoted-identifier-transact-sql
        MICROSOFT_SQL_SERVER);
  }

  @ParameterizedTest
  @MethodSource("identifierDialectDbSystemNames")
  void getSqlDialectTreatsDoubleQuotesAsIdentifiers(String dbSystemName) {
    DbRequest request =
        DbRequest.create(DbInfo.builder().dbSystemName(dbSystemName).build(), "SELECT 1", false);

    assertThat(attributesGetter.getSqlDialect(request)).isEqualTo(DOUBLE_QUOTES_ARE_IDENTIFIERS);
  }

  @ParameterizedTest
  @MethodSource("stringLiteralDialectDbSystemNames")
  void getSqlDialectTreatsDoubleQuotesAsStringLiteralsByDefault(String dbSystemName) {
    DbRequest request =
        DbRequest.create(DbInfo.builder().dbSystemName(dbSystemName).build(), "SELECT 1", false);

    assertThat(attributesGetter.getSqlDialect(request))
        .isEqualTo(DOUBLE_QUOTES_ARE_STRING_LITERALS);
  }
}
