/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.dbcp.v8_0;

import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolNameUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import javax.management.ObjectName;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.apache.tomcat.dbcp.dbcp2.OpenTelemetryBasicDataSourceUtil;

public class TomcatDbcpSingletons {
  public static String getDataSourceName(ObjectName objectName) {
    String name = objectName.getKeyProperty("name");
    return name != null ? name : objectName.toString();
  }

  public static String getDataSourceName(BasicDataSource dataSource) {
    DbInfo dbInfo =
        JdbcConnectionUrlParser.parse(
            dataSource.getUrl(),
            OpenTelemetryBasicDataSourceUtil.getConnectionProperties(dataSource));
    return JdbcConnectionPoolNameUtil.poolName(dbInfo, "tomcat-dbcp");
  }

  private TomcatDbcpSingletons() {}
}
