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
    if (request.getSqlQueryWithSummary() != null) {
      return request.getSqlQueryWithSummary().getQueryText();
    }
    if (request.getSqlQuery() != null) {
      return request.getSqlQuery().getQueryText();
    }
    return null;
  }

  @Nullable
  @Override
  public String getDbOperationName(InfluxDbRequest request) {
    if (request.getOperationName() != null) {
      return request.getOperationName();
    }
    if (request.getSqlQuery() != null) {
      return request.getSqlQuery().getOperationName();
    }
    return null;
  }

  @Nullable
  @Override
  public String getDbQuerySummary(InfluxDbRequest request) {
    if (request.getSqlQueryWithSummary() != null) {
      return request.getSqlQueryWithSummary().getQuerySummary();
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
