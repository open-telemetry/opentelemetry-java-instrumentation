/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class VertxSqlClientDataCapture implements VertxSqlClientDataProvider {

  private final List<Listener> listeners = new ArrayList<>();
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
            : new VertxSqlClientData(new SqlConnectOptions(connectOptions), capturedDbSystem, null);
    List<Listener> listenersToNotify;
    synchronized (this) {
      data = capturedData;
      if (capturedData == null || listeners.isEmpty()) {
        return;
      }
      listenersToNotify = new ArrayList<>(listeners);
      listeners.clear();
    }
    for (Listener listener : listenersToNotify) {
      listener.onCapture(capturedData);
    }
  }

  @Nullable
  public synchronized VertxSqlClientData addListener(Listener listener) {
    if (data == null) {
      listeners.add(listener);
    }
    return data;
  }

  public synchronized void removeListener(Listener listener) {
    listeners.remove(listener);
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
