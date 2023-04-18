/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import io.vertx.sqlclient.SqlConnectOptions;

public final class VertxSqlClientRequest {
  private final String statement;
  private final SqlConnectOptions sqlConnectOptions;

  public VertxSqlClientRequest(String statement, SqlConnectOptions sqlConnectOptions) {
    this.statement = statement;
    this.sqlConnectOptions = sqlConnectOptions;
  }

  public String getStatement() {
    return statement;
  }

  public String getUser() {
    return sqlConnectOptions != null ? sqlConnectOptions.getUser() : null;
  }

  public String getDatabase() {
    return sqlConnectOptions != null ? sqlConnectOptions.getDatabase() : null;
  }

  public String getHost() {
    return sqlConnectOptions != null ? sqlConnectOptions.getHost() : null;
  }

  public Integer getPort() {
    return sqlConnectOptions != null ? sqlConnectOptions.getPort() : null;
  }
}
