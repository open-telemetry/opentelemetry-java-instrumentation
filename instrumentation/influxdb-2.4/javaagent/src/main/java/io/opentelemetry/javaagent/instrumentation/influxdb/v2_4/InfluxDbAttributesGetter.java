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
    return request.getSql();
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
    return request.getDbName();
  }

  @Nullable
  @Override
  public String getConnectionString(InfluxDbRequest request) {
    return request.getConnectionString();
  }
}
