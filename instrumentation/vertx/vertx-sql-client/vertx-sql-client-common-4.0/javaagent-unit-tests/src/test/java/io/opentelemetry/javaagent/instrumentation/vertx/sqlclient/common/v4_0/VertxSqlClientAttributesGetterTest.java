/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.mysqlclient.MySQLException;
import org.junit.jupiter.api.Test;

class VertxSqlClientAttributesGetterTest {

  private final VertxSqlClientAttributesGetter getter = new VertxSqlClientAttributesGetter();
  private final VertxSqlClientRequest request =
      new VertxSqlClientRequest(
          "select 1", VertxSqlClientInfo.createUnknown(MYSQL), false, null);

  @Test
  void prefersMysqlVendorCodeWithoutChangingErrorType() {
    MySQLException error = new MySQLException("Syntax error", 1064, "42000");

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("1064");
    assertThat(getter.getErrorType(request, null, error)).isNull();
  }

  @Test
  void fallsBackToMysqlSqlStateWhenVendorCodeIsUnavailable() {
    MySQLException error = new MySQLException("Syntax error", 0, "42000");

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("42000");
    assertThat(getter.getErrorType(request, null, error)).isNull();
  }

  @Test
  void keepsMysqlVendorCodeWhenSqlStateIsUnavailable() {
    MySQLException error = new MySQLException("Syntax error", 1064, null);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("1064");
  }
}
