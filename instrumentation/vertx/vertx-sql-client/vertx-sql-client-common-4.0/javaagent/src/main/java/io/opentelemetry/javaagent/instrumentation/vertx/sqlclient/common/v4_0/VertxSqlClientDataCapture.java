/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

/**
 * Marks a client whose target is only known once a connection is established, because its connect
 * options come from a supplier that runs per connection attempt. Such a client never has client
 * wide data, so {@link #get()} always returns {@code null} and callers wait for the data attached
 * to the connection instead.
 */
public class VertxSqlClientDataCapture implements VertxSqlClientDataProvider {

  @Nullable private volatile String dbSystem;
  private final Map<Throwable, ArrayDeque<VertxSqlClientData>> failureData = new WeakHashMap<>();

  public void setDbSystem(@Nullable String dbSystem) {
    this.dbSystem = dbSystem;
  }

  @Nullable
  public String getDbSystem() {
    return dbSystem;
  }

  public synchronized void addFailureData(Throwable throwable, VertxSqlClientData data) {
    ArrayDeque<VertxSqlClientData> failures = failureData.get(throwable);
    if (failures == null) {
      failures = new ArrayDeque<>();
      failureData.put(throwable, failures);
    }
    failures.addLast(data);
  }

  @Nullable
  public synchronized VertxSqlClientData takeFailureData(Throwable throwable) {
    ArrayDeque<VertxSqlClientData> failures = failureData.get(throwable);
    if (failures == null) {
      return null;
    }
    VertxSqlClientData data = failures.pollFirst();
    if (failures.isEmpty()) {
      failureData.remove(throwable);
    }
    return data;
  }

  @Override
  @Nullable
  public VertxSqlClientData get() {
    return null;
  }
}
