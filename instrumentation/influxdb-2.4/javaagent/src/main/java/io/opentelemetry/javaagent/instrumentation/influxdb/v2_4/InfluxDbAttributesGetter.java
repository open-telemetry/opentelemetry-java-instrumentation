/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

final class InfluxDbAttributesGetter implements DbClientAttributesGetter<InfluxDbRequest> {

  @Nullable
  @Override
  public String getStatement(InfluxDbRequest request) {
    return request.getSqlStatementInfo().getFullStatement();
  }

  @Nullable
  @Override
  public String getOperation(InfluxDbRequest request) {
    if (request.getSqlStatementInfo() != null) {
      String operation = request.getSqlStatementInfo().getOperation();
      return StringUtils.isNullOrEmpty(operation) ? request.getSql() : operation;
    }
    return null;
  }

  @Nullable
  @Override
  public String getSystem(InfluxDbRequest request) {
    return "influxdb";
  }

  @Nullable
  @Override
  public String getUser(InfluxDbRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getName(InfluxDbRequest request) {
    String dbName = request.getDbName();
    return StringUtils.isNullOrEmpty(dbName) ? null : dbName;
  }
}
