/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum R2dbcSqlAttributesGetter implements SqlClientAttributesGetter<DbExecution, Void> {
  INSTANCE;

  @Override
  public String getDbSystem(DbExecution request) {
    return request.getSystem();
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(DbExecution request) {
    return request.getUser();
  }

  @Override
  @Nullable
  public String getDbNamespace(DbExecution request) {
    return request.getName();
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(DbExecution request) {
    return request.getConnectionString();
  }

  @Override
  @Nullable
  public String getRawQueryText(DbExecution request) {
    return request.getRawQueryText();
  }

  @Nullable
  @Override
  public String getServerAddress(DbExecution request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbExecution request) {
    return request.getPort();
  }
}
