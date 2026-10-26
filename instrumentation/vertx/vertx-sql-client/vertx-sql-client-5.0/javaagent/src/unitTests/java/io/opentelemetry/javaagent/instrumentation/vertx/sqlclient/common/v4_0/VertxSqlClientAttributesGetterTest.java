/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.IBM_DB2;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ORACLE_DB;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.sqlclient.DatabaseException;
import java.io.IOException;
import java.net.ConnectException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VertxSqlClientAttributesGetterTest {

  private final VertxSqlClientAttributesGetter getter = new VertxSqlClientAttributesGetter();
  private final VertxSqlClientRequest request = request(ORACLE_DB);

  @Test
  void omitsClientConnectionStatusWithoutChangingErrorType() {
    SQLException cause =
        new SQLException(
            "No listener",
            "66000",
            12541,
            new IOException("Oracle network failure", new ConnectException("Connection refused")));
    DatabaseException error = new DatabaseException("No listener", 12541, "66000", cause) {};

    assertThat(getter.getErrorType(request, null, error)).isEqualTo("66000");
    assertThat(getter.getDbResponseStatusCode(request, null, error)).isNull();
  }

  @Test
  void keepsServerStatusWithoutTransportFailure() {
    SQLException cause = new SQLException("Server failure", "66000", 12541);
    DatabaseException error = new DatabaseException("Server failure", 12541, "66000", cause) {};

    assertThat(getter.getErrorType(request, null, error)).isEqualTo("66000");
    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("12541");
  }

  @Test
  void prefersMysqlVendorCodeWithoutChangingErrorType() {
    VertxSqlClientRequest mysqlRequest = request(MYSQL);
    DatabaseException error = new DatabaseException("Syntax error", 1064, "42000") {};

    assertThat(getter.getErrorType(mysqlRequest, null, error)).isEqualTo("42000");
    assertThat(getter.getDbResponseStatusCode(mysqlRequest, null, error)).isEqualTo("1064");
  }

  @Test
  void usesMysqlVendorCodeWhenSqlStateIsUnavailable() {
    VertxSqlClientRequest mysqlRequest = request(MYSQL);
    DatabaseException error = new DatabaseException("Syntax error", 1064, null) {};

    assertThat(getter.getErrorType(mysqlRequest, null, error)).isEqualTo("null");
    assertThat(getter.getDbResponseStatusCode(mysqlRequest, null, error)).isEqualTo("1064");
  }

  @Test
  void fallsBackToSqlStateWhenVendorCodeIsUnavailable() {
    VertxSqlClientRequest mysqlRequest = request(MYSQL);
    DatabaseException error = new DatabaseException("Syntax error", 0, "42000") {};

    assertThat(getter.getErrorType(mysqlRequest, null, error)).isEqualTo("42000");
    assertThat(getter.getDbResponseStatusCode(mysqlRequest, null, error)).isEqualTo("42000");
  }

  @ParameterizedTest
  @ValueSource(strings = {MICROSOFT_SQL_SERVER, IBM_DB2})
  void prefersVendorCodeWithoutChangingErrorType(String dbSystemName) {
    VertxSqlClientRequest request = request(dbSystemName);
    DatabaseException error = new DatabaseException("Syntax error", 208, "42000") {};

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("208");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo("42000");
  }

  @Test
  void omitsNullSqlStateWithoutChangingErrorType() {
    DatabaseException error = new DatabaseException("Unknown SQLSTATE", 0, null) {};

    assertThat(getter.getErrorType(request, null, error)).isEqualTo("null");
    assertThat(getter.getDbResponseStatusCode(request, null, error)).isNull();
  }

  @Test
  void handlesCyclicCauseChain() {
    SQLException cause = new SQLException("Server failure", "66000", 12541);
    DatabaseException error = new DatabaseException("Server failure", 12541, "66000", cause) {};
    cause.initCause(error);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("12541");
  }

  private static VertxSqlClientRequest request(String dbSystemName) {
    return new VertxSqlClientRequest(
        "select 1", VertxSqlClientInfo.createUnknown(dbSystemName), false, null);
  }
}
