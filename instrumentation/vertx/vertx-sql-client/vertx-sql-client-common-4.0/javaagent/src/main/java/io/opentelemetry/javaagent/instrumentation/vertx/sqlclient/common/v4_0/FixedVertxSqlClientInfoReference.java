/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import javax.annotation.Nullable;

public final class FixedVertxSqlClientInfoReference implements VertxSqlClientInfoReference {

  @Nullable private final VertxSqlClientInfo info;

  public FixedVertxSqlClientInfoReference(@Nullable VertxSqlClientInfo info) {
    this.info = info;
  }

  @Override
  @Nullable
  public VertxSqlClientInfo get() {
    return info;
  }
}
