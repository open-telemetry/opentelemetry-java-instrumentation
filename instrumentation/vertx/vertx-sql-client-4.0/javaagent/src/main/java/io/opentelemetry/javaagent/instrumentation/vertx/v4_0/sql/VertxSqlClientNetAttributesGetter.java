/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

public enum VertxSqlClientNetAttributesGetter
    implements NetClientAttributesGetter<VertxSqlClientRequest, Void> {
  INSTANCE;

  @Nullable
  @Override
  public String getTransport(VertxSqlClientRequest request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getPeerName(VertxSqlClientRequest request) {
    SqlConnectOptions sqlConnectOptions = request.getSqlConnectOptions();
    return sqlConnectOptions != null ? sqlConnectOptions.getHost() : null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(VertxSqlClientRequest request) {
    SqlConnectOptions sqlConnectOptions = request.getSqlConnectOptions();
    return sqlConnectOptions != null ? sqlConnectOptions.getPort() : null;
  }
}
