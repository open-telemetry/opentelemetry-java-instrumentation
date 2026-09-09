/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

public class VertxSqlClientData {
  @Nullable private final SqlConnectOptions connectOptions;
  @Nullable private final String dbSystem;

  public VertxSqlClientData(@Nullable SqlConnectOptions connectOptions, @Nullable String dbSystem) {
    this.connectOptions = connectOptions;
    this.dbSystem = dbSystem;
  }

  @Nullable
  public SqlConnectOptions getConnectOptions() {
    return connectOptions;
  }

  @Nullable
  public String getDbSystem() {
    return dbSystem;
  }
}
