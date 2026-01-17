/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

final class InfluxDbAttributesGetter implements DbClientAttributesGetter<InfluxDbRequest, Void> {

  @Nullable
  @Override
  public String getDbQueryText(InfluxDbRequest request) {
    return request.getSqlStatementInfo().getQueryText();
  }

  @Nullable
  @Override
  public String getDbQuerySummary(InfluxDbRequest request) {
    return request.getSqlStatementInfo().getQuerySummary();
  }

  @Nullable
  @Override
  public String getDbOperationName(InfluxDbRequest request) {
    if (request.getOperation() != null) {
      return request.getOperation();
    }
    if (SemconvStability.emitStableDatabaseSemconv()) {
      // Under stable semconv, operation name should not be extracted from query text
      return null;
    }
    return request.getSqlStatementInfo().getOperationName();
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
