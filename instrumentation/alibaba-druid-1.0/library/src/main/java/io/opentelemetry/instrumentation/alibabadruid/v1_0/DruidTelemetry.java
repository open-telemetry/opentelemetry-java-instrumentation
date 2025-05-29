/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadruid.v1_0;

import com.alibaba.druid.pool.DruidDataSourceMBean;
import io.opentelemetry.api.OpenTelemetry;

public final class DruidTelemetry {

  public static DruidTelemetry create(OpenTelemetry openTelemetry) {
    return new DruidTelemetry(openTelemetry);
  }

  private final OpenTelemetry openTelemetry;

  private DruidTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public void registerMetrics(DruidDataSourceMBean dataSource, String dataSourceName) {
    ConnectionPoolMetrics.registerMetrics(openTelemetry, dataSource, dataSourceName);
  }

  public void unregisterMetrics(DruidDataSourceMBean dataSource) {
    ConnectionPoolMetrics.unregisterMetrics(dataSource);
  }
}
