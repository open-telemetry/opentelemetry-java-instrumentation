/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sql;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

enum VertxSqlClientNetAttributesGetter implements ServerAttributesGetter<VertxSqlClientRequest> {
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
