/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;

/** Coordinates requests with per-connection options supplied after the request starts. */
public final class VertxSqlClientInfoCapture implements VertxSqlClientInfoProvider {

  private final Deque<Object> connectionRequests = new ArrayDeque<>();
  @Nullable private volatile String dbSystemName;
  @Nullable private volatile VertxSqlClientInfo info;

  public void setDbSystemName(@Nullable String dbSystemName) {
    this.dbSystemName = dbSystemName;
  }

  @Nullable
  public String getDbSystemName() {
    return dbSystemName;
  }

  public void setInfo(@Nullable VertxSqlClientInfo info) {
    this.info = info;
  }

  public synchronized void addConnectionRequest(Object request) {
    connectionRequests.addLast(request);
  }

  @Nullable
  public synchronized Object takeConnectionRequest() {
    return connectionRequests.pollFirst();
  }

  public synchronized void removeConnectionRequest(Object request) {
    connectionRequests.remove(request);
  }

  @Override
  @Nullable
  public VertxSqlClientInfo getInfo() {
    return info;
  }
}
