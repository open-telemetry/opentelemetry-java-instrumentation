/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import javax.annotation.Nullable;

public final class VertxSqlClientInfoReference {

  @Nullable private volatile VertxSqlClientInfo info;

  public VertxSqlClientInfoReference(@Nullable VertxSqlClientInfo info) {
    this.info = info;
  }

  public void set(@Nullable VertxSqlClientInfo info) {
    this.info = info;
  }

  @Nullable
  public VertxSqlClientInfo get() {
    return info;
  }
}
