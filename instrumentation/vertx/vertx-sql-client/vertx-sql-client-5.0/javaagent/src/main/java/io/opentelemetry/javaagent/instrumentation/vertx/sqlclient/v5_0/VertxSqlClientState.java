/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;

public final class VertxSqlClientState {
  private final VertxSqlClientInfo info;
  private final boolean supplier;

  public VertxSqlClientState(VertxSqlClientInfo info, boolean supplier) {
    this.info = info;
    this.supplier = supplier;
  }

  public VertxSqlClientInfo getInfo() {
    return info;
  }

  public boolean isSupplier() {
    return supplier;
  }
}
