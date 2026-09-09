/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;

public final class VertxSqlClientSupplierInfo {
  private final VertxSqlClientInfo info;

  public VertxSqlClientSupplierInfo(VertxSqlClientInfo info) {
    this.info = info;
  }

  public VertxSqlClientInfo getInfo() {
    return info;
  }
}
