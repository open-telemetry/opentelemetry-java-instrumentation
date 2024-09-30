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

  @Nullable
  @Override
  public String getDbSystem(VertxSqlClientRequest request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(VertxSqlClientRequest request) {
    return request.getUser();
  }

  @Nullable
  @Override
  public String getDbNamespace(VertxSqlClientRequest request) {
    return request.getDatabase();
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(VertxSqlClientRequest request) {
    return null;
  }

  @Override
  public String getRawQueryText(VertxSqlClientRequest request) {
    return request.getDbQueryText();
  }
}
