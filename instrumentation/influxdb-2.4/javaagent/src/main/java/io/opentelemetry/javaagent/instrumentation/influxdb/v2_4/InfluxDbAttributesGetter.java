/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nullable;

final class InfluxDbAttributesGetter implements SqlClientAttributesGetter<InfluxDbRequest, Void> {

  @Override
  public Collection<String> getRawQueryTexts(InfluxDbRequest request) {
    String sql = request.getSql();
    if (sql == null) {
      return Collections.emptyList();
    }
    return Collections.singletonList(sql);
  }

  @Nullable
  @Override
  public String getDbOperationName(InfluxDbRequest request) {
    return request.getOperationName();
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

  @Override
  public String getServerAddress(InfluxDbRequest request) {
    return request.getHost();
  }

  @Override
  public Integer getServerPort(InfluxDbRequest request) {
    return request.getPort();
  }
}
