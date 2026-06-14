/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

final class InfluxDbAttributesGetter implements DbClientAttributesGetter<InfluxDbOperation, Void> {

  @Nullable
  @Override
  public String getDbOperationName(InfluxDbOperation request) {
    return request.getOperation();
  }

  @Nullable
  @Override
  @SuppressWarnings("deprecation") // old database semconv still use db.operation
  public String getDbOperation(InfluxDbOperation request) {
    String operation = request.getOperation();
    if ("createDatabase".equals(operation)) {
      return "CREATE DATABASE";
    } else if ("deleteDatabase".equals(operation)) {
      return "DROP DATABASE";
    } else if ("write".equals(operation)) {
      return "WRITE";
    }
    return operation;
  }

  @Override
  public String getDbSystemName(InfluxDbOperation request) {
    return "influxdb";
  }

  @Nullable
  @Override
  public String getDbNamespace(InfluxDbOperation request) {
    return request.getNamespace();
  }

  @Nullable
  @Override
  public String getDbQueryText(InfluxDbOperation request) {
    return null;
  }

  @Override
  public String getServerAddress(InfluxDbOperation request) {
    return request.getHost();
  }

  @Override
  public Integer getServerPort(InfluxDbOperation request) {
    return request.getPort();
  }
}
