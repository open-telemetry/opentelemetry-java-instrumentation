/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import io.vertx.sqlclient.SqlConnectOptions;

public final class VertxSqlClientRequest {
  private final String queryText;
  private final SqlConnectOptions sqlConnectOptions;

  public VertxSqlClientRequest(String queryText, SqlConnectOptions sqlConnectOptions) {
    this.queryText = queryText;
    this.sqlConnectOptions = sqlConnectOptions;
  }

  public String getQueryText() {
    return queryText;
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
