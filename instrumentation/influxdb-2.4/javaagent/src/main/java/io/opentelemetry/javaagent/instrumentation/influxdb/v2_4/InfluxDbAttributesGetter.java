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
    return request.getSqlStatementInfo().getQueryText();
  }

  @Nullable
  @Override
  public String getDbOperationName(InfluxDbRequest request) {
    if (request.getOperationName() != null) {
      return request.getOperationName();
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
    String namespace = request.getNamespace();
    return "".equals(namespace) ? null : namespace;
  }
}
