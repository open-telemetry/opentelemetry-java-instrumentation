/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesGetter;
import javax.annotation.Nullable;

final class R2dbcSqlAttributesGetter implements SqlClientAttributesGetter<DbExecution> {

  @Override
  public String getSystem(DbExecution request) {
    return request.getSystem();
  }

  @Override
  @Nullable
  public String getUser(DbExecution request) {
    return request.getUser();
  }

  @Override
  @Nullable
  public String getName(DbExecution request) {
    return request.getName();
  }

  @Override
  @Nullable
  public String getConnectionString(DbExecution request) {
    return request.getConnectionString();
  }

  @Override
  @Nullable
  public String getRawStatement(DbExecution request) {
    return request.getRawStatement();
  }
}
