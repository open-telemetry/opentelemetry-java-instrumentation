/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributeGetter;
import javax.annotation.Nullable;

public enum VertxSqlClientNetAttributeGetter
    implements ServerAttributeGetter<VertxSqlClientRequest> {
  INSTANCE;

  @Nullable
  @Override
  public String getServerAddress(VertxSqlClientRequest request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(VertxSqlClientRequest request) {
    return request.getPort();
  }
}
