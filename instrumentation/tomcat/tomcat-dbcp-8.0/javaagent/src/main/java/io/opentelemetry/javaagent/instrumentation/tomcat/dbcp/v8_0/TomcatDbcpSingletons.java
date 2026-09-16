/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.dbcp.v8_0;

import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import javax.annotation.Nullable;
import javax.management.ObjectName;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.apache.tomcat.dbcp.dbcp2.OpenTelemetryBasicDataSourceUtil;

public class TomcatDbcpSingletons {
  public static String getDataSourceName(ObjectName objectName) {
    String name = objectName.getKeyProperty("name");
    return name != null ? name : objectName.toString();
  }

  public static void registerMetrics(BasicDataSource dataSource, @Nullable String poolName) {
    DbInfo dbInfo = getDbInfo(dataSource);
    TomcatDbcpDataSourceMetrics.registerMetrics(
        dataSource,
        JdbcConnectionPoolMetricsUtil.poolName(dbInfo, poolName, "tomcat-dbcp"),
        JdbcConnectionPoolMetricsUtil.databaseAttributes(dbInfo));
  }

  private static DbInfo getDbInfo(BasicDataSource dataSource) {
    return JdbcConnectionUrlParser.parse(
        dataSource.getUrl(), OpenTelemetryBasicDataSourceUtil.getConnectionProperties(dataSource));
  }

  private TomcatDbcpSingletons() {}
}
