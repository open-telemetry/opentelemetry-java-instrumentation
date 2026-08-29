/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

public class VertxSqlClientDataCapture implements VertxSqlClientDataProvider {

  @Nullable private volatile VertxSqlClientData data;

  public void capture(@Nullable SqlConnectOptions connectOptions, @Nullable String dbSystem) {
    if (connectOptions == null) {
      data = null;
      return;
    }
    data = new VertxSqlClientData(new SqlConnectOptions(connectOptions), dbSystem, null);
  }

  @Override
  @Nullable
  public VertxSqlClientData get() {
    return data;
  }

  public interface Listener {
    void onCapture(VertxSqlClientData data);
  }
}
