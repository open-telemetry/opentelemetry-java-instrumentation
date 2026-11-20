/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ORACLE_DB;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.sqlclient.DatabaseException;
import org.junit.jupiter.api.Test;

class VertxSqlClientAttributesGetterTest {

  private final VertxSqlClientAttributesGetter getter = new VertxSqlClientAttributesGetter();
  private final VertxSqlClientRequest request = request(ORACLE_DB);

  @Test
  void capturesOracleConnectionStatus() {
    DatabaseException error = new DatabaseException("No listener", 12541, "66000") {};

    assertThat(getter.getErrorType(request, null, error)).isEqualTo("12541");
    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("12541");
  }

  @Test
  void prefersMysqlVendorCode() {
    VertxSqlClientRequest mysqlRequest = request(MYSQL);
    DatabaseException error = new DatabaseException("Syntax error", 1064, "42000") {};

    assertThat(getter.getErrorType(mysqlRequest, null, error)).isEqualTo("1064");
    assertThat(getter.getDbResponseStatusCode(mysqlRequest, null, error)).isEqualTo("1064");
  }

  @Test
  void capturesSqlStateSuccessAndPrefersPostgresqlSqlState() {
    VertxSqlClientRequest mysqlRequest = request(MYSQL);
    DatabaseException successful = new DatabaseException("Success", 0, "00000") {};
    VertxSqlClientRequest postgresRequest = request(POSTGRESQL);
    DatabaseException postgresError = new DatabaseException("Syntax error", 42, "42601") {};
    DatabaseException postgresSuccess = new DatabaseException("Success", 42, "00000") {};

    assertThat(getter.getDbResponseStatusCode(mysqlRequest, null, successful)).isEqualTo("00000");
    assertThat(getter.getErrorType(mysqlRequest, null, successful))
        .isEqualTo(successful.getClass().getName());
    assertThat(getter.getDbResponseStatusCode(postgresRequest, null, postgresError))
        .isEqualTo("42601");
    assertThat(getter.getErrorType(postgresRequest, null, postgresError)).isEqualTo("42601");
    assertThat(getter.getDbResponseStatusCode(postgresRequest, null, postgresSuccess))
        .isEqualTo("00000");
    assertThat(getter.getErrorType(postgresRequest, null, postgresSuccess))
        .isEqualTo(postgresSuccess.getClass().getName());
  }

  @Test
  void omitsUnavailableCodes() {
    DatabaseException error = new DatabaseException("Unknown SQLSTATE", 0, null) {};

    assertThat(getter.getErrorType(request, null, error)).isEqualTo(error.getClass().getName());
    assertThat(getter.getDbResponseStatusCode(request, null, error)).isNull();
  }

  private static VertxSqlClientRequest request(String dbSystemName) {
    return new VertxSqlClientRequest(
        "select 1", VertxSqlClientInfo.createUnknown(dbSystemName), false, null);
  }
}
