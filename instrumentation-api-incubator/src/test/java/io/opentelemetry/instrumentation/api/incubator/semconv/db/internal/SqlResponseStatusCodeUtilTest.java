/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SqlResponseStatusCodeUtilTest {

  @ParameterizedTest
  @MethodSource("responseStatusCodes")
  void selectsResponseStatusCode(
      String dbSystemName, String sqlState, String vendorCode, String expected) {
    assertThat(SqlResponseStatusCodeUtil.getResponseStatusCode(dbSystemName, sqlState, vendorCode))
        .isEqualTo(expected);
  }

  private static Stream<Arguments> responseStatusCodes() {
    return Stream.of(
        argumentSet("DB2 prefers vendor code", "ibm.db2", "42000", "42", "42"),
        argumentSet("MariaDB prefers vendor code", "mariadb", "42000", "42", "42"),
        argumentSet("SQL Server prefers vendor code", "microsoft.sql_server", "42000", "42", "42"),
        argumentSet("MySQL prefers vendor code", "mysql", "42000", "42", "42"),
        argumentSet("Oracle prefers vendor code", "oracle.db", "42000", "42", "42"),
        argumentSet("SQLite prefers vendor code", "sqlite", "42000", "42", "42"),
        argumentSet("PostgreSQL prefers SQLSTATE", "postgresql", "42601", "42", "42601"),
        argumentSet("ambiguous codes are combined", "other", "42000", "42", "42000/42"),
        argumentSet("SQLSTATE fallback", "other", "42000", null, "42000"),
        argumentSet("vendor code fallback", "other", null, "42", "42"),
        argumentSet("no codes", "other", null, null, null),
        argumentSet("empty codes are unavailable", "other", "", "", null));
  }
}
