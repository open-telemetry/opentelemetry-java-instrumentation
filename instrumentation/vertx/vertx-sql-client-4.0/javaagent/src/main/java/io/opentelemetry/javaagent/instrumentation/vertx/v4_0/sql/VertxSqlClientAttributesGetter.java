/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesGetter;
import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum VertxSqlClientAttributesGetter
    implements SqlClientAttributesGetter<VertxSqlClientRequest> {
  INSTANCE;

  @Override
  public String getSystem(VertxSqlClientRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getUser(VertxSqlClientRequest request) {
    SqlConnectOptions sqlConnectOptions = request.getSqlConnectOptions();
    return sqlConnectOptions != null ? sqlConnectOptions.getUser() : null;
  }

  @Override
  @Nullable
  public String getName(VertxSqlClientRequest request) {
    SqlConnectOptions sqlConnectOptions = request.getSqlConnectOptions();
    return sqlConnectOptions != null ? sqlConnectOptions.getDatabase() : null;
  }

  @Override
  @Nullable
  public String getConnectionString(VertxSqlClientRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getRawStatement(VertxSqlClientRequest request) {
    return request.getStatement();
  }
}
