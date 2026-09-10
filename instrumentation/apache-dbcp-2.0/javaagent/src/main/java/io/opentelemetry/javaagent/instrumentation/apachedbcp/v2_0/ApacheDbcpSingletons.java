/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedbcp.v2_0.ApacheDbcpTelemetry;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsInfo;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolNameUtil;
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

  public static JdbcConnectionPoolMetricsInfo getMetricsInfo(
      BasicDataSource dataSource, @Nullable String poolName) {
    DbInfo dbInfo = getDbInfo(dataSource);
    return poolName == null
        ? JdbcConnectionPoolNameUtil.createMetricsInfo(dbInfo, "apache-dbcp2")
        : JdbcConnectionPoolNameUtil.createMetricsInfoWithPoolName(dbInfo, poolName);
  }

  private static DbInfo getDbInfo(BasicDataSource dataSource) {
    DbInfo dbInfo =
        JdbcConnectionUrlParser.parse(
            dataSource.getUrl(),
            OpenTelemetryBasicDataSourceUtil.getConnectionProperties(dataSource));
    return dbInfo;
  }

  private ApacheDbcpSingletons() {}
}
