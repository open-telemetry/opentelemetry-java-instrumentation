/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.IBM_DB2;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.db2client.DB2Exception;
import io.vertx.mssqlclient.MSSQLException;
import io.vertx.mysqlclient.MySQLException;
import org.junit.jupiter.api.Test;

class VertxSqlClientAttributesGetterTest {

  private final VertxSqlClientAttributesGetter getter = new VertxSqlClientAttributesGetter();
  private final VertxSqlClientRequest request =
      new VertxSqlClientRequest("select 1", VertxSqlClientInfo.createUnknown(MYSQL), false, null);

  @Test
  void prefersMysqlVendorCodeWithoutChangingErrorType() {
    MySQLException error = new MySQLException("Syntax error", 1064, "42000");

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("1064");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo(MySQLException.class.getName());
  }

  @Test
  void fallsBackToMysqlSqlStateWhenVendorCodeIsUnavailable() {
    MySQLException error = new MySQLException("Syntax error", 0, "42000");

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("42000");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo(MySQLException.class.getName());
  }

  @Test
  void keepsMysqlVendorCodeWhenSqlStateIsUnavailable() {
    MySQLException error = new MySQLException("Syntax error", 1064, null);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("1064");
  }

  @Test
  void keepsSuccessfulSqlStateAndCombinesUnknownSystemCodes() {
    MySQLException successful = new MySQLException("Success", 0, "00000");
    VertxSqlClientRequest unknownRequest =
        new VertxSqlClientRequest(
            "select 1", VertxSqlClientInfo.createUnknown("unknown"), false, null);
    MySQLException unknown = new MySQLException("Syntax error", 1064, "42000");

    assertThat(getter.getDbResponseStatusCode(request, null, successful)).isEqualTo("00000");
    assertThat(getter.getDbResponseStatusCode(unknownRequest, null, unknown))
        .isEqualTo("42000/1064");
  }

  @Test
  void extractsLegacyMssqlErrorCodeWithoutChangingErrorType() {
    VertxSqlClientRequest request =
        new VertxSqlClientRequest(
            "select 1", VertxSqlClientInfo.createUnknown(MICROSOFT_SQL_SERVER), false, null);
    MSSQLException error =
        new MSSQLException(208, (byte) 1, (byte) 16, "Invalid object", null, null, 1);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("208");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo(MSSQLException.class.getName());
  }

  @Test
  void extractsLegacyDb2ErrorCodeAndFallsBackToSqlState() {
    VertxSqlClientRequest request =
        new VertxSqlClientRequest(
            "select 1", VertxSqlClientInfo.createUnknown(IBM_DB2), false, null);
    DB2Exception error = new DB2Exception(-204, "42704");
    DB2Exception errorWithoutCode = new DB2Exception("Unknown code", 0, "42704");

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("-204");
    assertThat(getter.getDbResponseStatusCode(request, null, errorWithoutCode)).isEqualTo("42704");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo(DB2Exception.class.getName());
  }
}
