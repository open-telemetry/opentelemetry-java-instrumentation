/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import io.vertx.sqlclient.SqlConnectOptions;

public final class VertxSqlClientRequest {
  private final String rawQueryText;
  private final SqlConnectOptions sqlConnectOptions;

  public VertxSqlClientRequest(String rawQueryText, SqlConnectOptions sqlConnectOptions) {
    this.rawQueryText = rawQueryText;
    this.sqlConnectOptions = sqlConnectOptions;
  }

  public String getRawQueryText() {
    return rawQueryText;
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
