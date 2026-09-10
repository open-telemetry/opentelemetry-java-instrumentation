/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0;

import com.alibaba.druid.pool.DruidAbstractDataSource;
import com.alibaba.druid.pool.DruidDataSourceMBean;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.alibabadruid.v1_0.DruidTelemetry;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.util.Properties;
import javax.annotation.Nullable;

public class DruidSingletons {

  private static final DruidTelemetry telemetry = DruidTelemetry.create(GlobalOpenTelemetry.get());

  public static void registerMetrics(DruidDataSourceMBean dataSource, @Nullable String poolName) {
    DbInfo dbInfo = getDbInfo(dataSource);
    telemetry()
        .registerMetrics(
            dataSource,
            JdbcConnectionPoolMetricsUtil.poolName(
                dbInfo, poolName == null || poolName.isEmpty() ? null : poolName, "alibaba-druid"),
            JdbcConnectionPoolMetricsUtil.databaseAttributes(dbInfo));
  }

  private static DbInfo getDbInfo(DruidDataSourceMBean dataSource) {
    Properties connectProperties = null;
    if (dataSource instanceof DruidAbstractDataSource) {
      connectProperties = ((DruidAbstractDataSource) dataSource).getConnectProperties();
    }

    return JdbcConnectionUrlParser.parse(dataSource.getUrl(), connectProperties);
  }

  public static DruidTelemetry telemetry() {
    return telemetry;
  }

  private DruidSingletons() {}
}
