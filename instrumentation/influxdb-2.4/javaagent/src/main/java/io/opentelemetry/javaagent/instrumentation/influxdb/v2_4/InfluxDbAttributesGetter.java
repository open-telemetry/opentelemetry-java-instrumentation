/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import javax.annotation.Nullable;

final class InfluxDbAttributesGetter implements DbClientAttributesGetter<InfluxDbRequest> {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(CommonConfig.get().isStatementSanitizationEnabled());

  @Nullable
  @Override
  public String getStatement(InfluxDbRequest request) {
    return sanitizer.sanitize(request.getSql()).getFullStatement();
  }

  @Nullable
  @Override
  public String getOperation(InfluxDbRequest request) {
    String sql = request.getSql();
    return StringUtils.isNullOrEmpty(sql) ? null : sql.split(" ")[0];
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

  @Nullable
  @Override
  public String getConnectionString(InfluxDbRequest request) {
    return request.getConnectionString();
  }
}
