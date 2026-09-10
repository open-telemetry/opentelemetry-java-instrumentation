/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedbcp.v2_0.ApacheDbcpTelemetry;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import javax.annotation.Nullable;
import javax.management.ObjectName;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.OpenTelemetryBasicDataSourceUtil;

public class ApacheDbcpSingletons {

  private static final ApacheDbcpTelemetry telemetry =
      ApacheDbcpTelemetry.create(GlobalOpenTelemetry.get());

  public static ApacheDbcpTelemetry telemetry() {
    return telemetry;
  }

  public static String getDataSourceName(ObjectName objectName) {
    String name = objectName.getKeyProperty("name");
    return name != null ? name : objectName.toString();
  }

  public static void registerMetrics(BasicDataSource dataSource, @Nullable String poolName) {
    DbInfo dbInfo = getDbInfo(dataSource);
    telemetry()
        .registerMetrics(
            dataSource,
            JdbcConnectionPoolMetricsUtil.poolName(dbInfo, poolName, "apache-dbcp2"),
            JdbcConnectionPoolMetricsUtil.databaseAttributes(dbInfo));
  }

  private static DbInfo getDbInfo(BasicDataSource dataSource) {
    return JdbcConnectionUrlParser.parse(
        dataSource.getUrl(), OpenTelemetryBasicDataSourceUtil.getConnectionProperties(dataSource));
  }

  private ApacheDbcpSingletons() {}
}
