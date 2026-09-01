/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import java.util.ArrayDeque;
import javax.annotation.Nullable;

/**
 * Marks a client whose target is only known once a connection is established, because its connect
 * options come from a supplier that runs per connection attempt. Such a client never has client
 * wide data, so {@link #get()} always returns {@code null} and callers wait for the data attached
 * to the connection instead.
 */
public class VertxSqlClientDataCapture implements VertxSqlClientDataProvider {

  @Nullable private volatile String dbSystem;
  private final ArrayDeque<Object> connectionRequests = new ArrayDeque<>();

  public void setDbSystem(@Nullable String dbSystem) {
    this.dbSystem = dbSystem;
  }

  @Nullable
  public String getDbSystem() {
    return dbSystem;
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
  public VertxSqlClientData get() {
    return null;
  }
}
