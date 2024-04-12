/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import javax.annotation.Nullable;

public enum VertxSqlClientAttributesGetter
    implements SqlClientAttributesGetter<VertxSqlClientRequest> {
  INSTANCE;

  @Override
  public String getSystem(VertxSqlClientRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getUser(VertxSqlClientRequest request) {
    return request.getUser();
  }

  @Override
  @Nullable
  public String getName(VertxSqlClientRequest request) {
    return request.getDatabase();
  }

  @Override
  @Nullable
  public String getRawStatement(VertxSqlClientRequest request) {
    return request.getStatement();
  }
}
