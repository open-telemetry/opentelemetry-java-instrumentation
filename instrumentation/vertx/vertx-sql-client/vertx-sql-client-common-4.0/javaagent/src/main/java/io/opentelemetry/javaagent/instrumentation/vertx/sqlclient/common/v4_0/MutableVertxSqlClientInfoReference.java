/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import javax.annotation.Nullable;

public final class MutableVertxSqlClientInfoReference implements VertxSqlClientInfoReference {

  @Nullable private volatile VertxSqlClientInfo info;

  public MutableVertxSqlClientInfoReference(@Nullable VertxSqlClientInfo info) {
    this.info = info;
  }

  public void set(@Nullable VertxSqlClientInfo info) {
    this.info = info;
  }

  @Override
  @Nullable
  public VertxSqlClientInfo get() {
    return info;
  }
}
