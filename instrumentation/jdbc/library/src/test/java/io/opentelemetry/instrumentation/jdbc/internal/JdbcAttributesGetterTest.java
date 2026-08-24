/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MARIADB;
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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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

  @ParameterizedTest
  @MethodSource("errorCodes")
  void getErrorTypeNormalizesVendorCode(int errorCode, String expectedErrorType) {
    DbRequest request =
        DbRequest.create(DbInfo.builder().dbSystemName(POSTGRESQL).build(), "SELECT 1", false);

    assertThat(
            attributesGetter.getErrorType(
                request, null, new SQLException("test", "state", errorCode)))
        .isEqualTo(expectedErrorType);
  }

  private static Stream<Arguments> errorCodes() {
    return Stream.of(
        argumentSet("positive", 42, "42"),
        argumentSet("negative", -42, "-42"),
        argumentSet("zero is unavailable", 0, null));
  }

  @Test
  void groupTargetReplacesHostAndPortOnlyInStableSemconv() {
    DbInfo dbInfo =
        DbInfo.builder()
            .dbSystemName(MARIADB)
            .serverAddress("h1")
            .serverPort(3306)
            .serverAddressGroup("h1:3306,h2:3306")
            .build();
    DbRequest request = DbRequest.create(dbInfo, "SELECT 1", false);

    if (emitStableDatabaseSemconv()) {
      assertThat(attributesGetter.getServerAddress(request)).isEqualTo("h1:3306,h2:3306");
      assertThat(attributesGetter.getServerPort(request)).isNull();
    } else {
      assertThat(attributesGetter.getServerAddress(request)).isEqualTo("h1");
      assertThat(attributesGetter.getServerPort(request)).isEqualTo(3306);
    }
  }

  @Test
  void singularTargetKeepsHostAndPortInEveryMode() {
    DbInfo dbInfo =
        DbInfo.builder().dbSystemName(MARIADB).serverAddress("h1").serverPort(3306).build();
    DbRequest request = DbRequest.create(dbInfo, "SELECT 1", false);

    assertThat(attributesGetter.getServerAddress(request)).isEqualTo("h1");
    assertThat(attributesGetter.getServerPort(request)).isEqualTo(3306);
  }
}
