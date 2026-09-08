/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0;

import com.alibaba.druid.pool.DruidAbstractDataSource;
import com.alibaba.druid.pool.DruidDataSourceMBean;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.alibabadruid.v1_0.DruidTelemetry;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolNameUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.util.Properties;

public class DruidSingletons {

  private static final DruidTelemetry telemetry = DruidTelemetry.create(GlobalOpenTelemetry.get());

  public static String getDataSourceName(DruidDataSourceMBean dataSource) {
    return JdbcConnectionPoolNameUtil.poolName(getDbInfo(dataSource), "alibaba-druid");
  }

  public static Attributes getDatabaseAttributes(DruidDataSourceMBean dataSource) {
    return JdbcConnectionPoolNameUtil.databaseAttributes(getDbInfo(dataSource));
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
