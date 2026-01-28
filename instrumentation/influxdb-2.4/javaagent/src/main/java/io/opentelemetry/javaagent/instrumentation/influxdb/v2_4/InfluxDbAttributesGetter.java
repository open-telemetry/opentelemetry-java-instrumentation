/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

final class InfluxDbAttributesGetter implements DbClientAttributesGetter<InfluxDbRequest, Void> {

  @Nullable
  @Override
  public String getDbQueryText(InfluxDbRequest request) {
    if (request.getSqlStatementInfoWithSummary() != null) {
      return request.getSqlStatementInfoWithSummary().getQueryText();
    }
    if (request.getSqlStatementInfo() != null) {
      return request.getSqlStatementInfo().getQueryText();
    }
    return null;
  }

  @Nullable
  @Override
  public String getDbOperationName(InfluxDbRequest request) {
    if (request.getOperation() != null) {
      return request.getOperation();
    }
    if (request.getSqlStatementInfo() != null) {
      return request.getSqlStatementInfo().getOperationName();
    }
    return null;
  }

  @Nullable
  @Override
  public String getDbQuerySummary(InfluxDbRequest request) {
    if (request.getSqlStatementInfoWithSummary() != null) {
      return request.getSqlStatementInfoWithSummary().getQuerySummary();
    }
    return null;
  }

  @Override
  public String getDbSystemName(InfluxDbRequest request) {
    return "influxdb";
  }

  @Nullable
  @Override
  public String getDbNamespace(InfluxDbRequest request) {
    String dbName = request.getDbName();
    return "".equals(dbName) ? null : dbName;
  }
}
