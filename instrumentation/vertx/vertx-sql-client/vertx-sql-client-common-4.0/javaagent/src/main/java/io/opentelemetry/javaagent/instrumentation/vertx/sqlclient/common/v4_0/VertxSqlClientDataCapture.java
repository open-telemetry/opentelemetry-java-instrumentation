/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

public class VertxSqlClientDataCapture implements VertxSqlClientDataProvider {

  @Nullable private volatile String dbSystem;
  @Nullable private volatile VertxSqlClientData data;

  public void setDbSystem(@Nullable String dbSystem) {
    this.dbSystem = dbSystem;
  }

  public void capture(@Nullable SqlConnectOptions connectOptions, @Nullable String dbSystem) {
    String capturedDbSystem = this.dbSystem != null ? this.dbSystem : dbSystem;
    VertxSqlClientData capturedData =
        connectOptions == null
            ? null
            : VertxSqlClientData.fromConnectOptions(connectOptions, capturedDbSystem);
    data = capturedData;
  }

  @Override
  @Nullable
  public VertxSqlClientData get() {
    return data;
  }
}
