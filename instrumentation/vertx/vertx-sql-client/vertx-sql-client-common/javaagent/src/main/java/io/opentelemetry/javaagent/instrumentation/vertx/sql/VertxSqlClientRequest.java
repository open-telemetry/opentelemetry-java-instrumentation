/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sql;

import io.vertx.sqlclient.SqlConnectOptions;

public final class VertxSqlClientRequest {
  private final String queryText;
  private final SqlConnectOptions sqlConnectOptions;
  private final boolean parameterizedQuery;

  public VertxSqlClientRequest(
      String queryText, SqlConnectOptions sqlConnectOptions, boolean parameterizedQuery) {
    this.queryText = queryText;
    this.sqlConnectOptions = sqlConnectOptions;
    this.parameterizedQuery = parameterizedQuery;
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

  public boolean isParameterizedQuery() {
    return parameterizedQuery;
  }
}
