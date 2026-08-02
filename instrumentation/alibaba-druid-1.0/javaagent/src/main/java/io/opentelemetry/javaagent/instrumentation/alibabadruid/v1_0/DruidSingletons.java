/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0;

import com.alibaba.druid.pool.DruidAbstractDataSource;
import com.alibaba.druid.pool.DruidDataSourceMBean;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.alibabadruid.v1_0.DruidTelemetry;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.util.Properties;

public class DruidSingletons {

  private static final DruidTelemetry telemetry = DruidTelemetry.create(GlobalOpenTelemetry.get());

  public static String getDataSourceName(DruidDataSourceMBean dataSource) {
    Properties connectProperties = null;
    if (dataSource instanceof DruidAbstractDataSource) {
      connectProperties = ((DruidAbstractDataSource) dataSource).getConnectProperties();
    }

    DbInfo dbInfo = JdbcConnectionUrlParser.parse(dataSource.getUrl(), connectProperties);
    String serverAddress = dbInfo.getServerAddress();
    Integer serverPort = dbInfo.getServerPort();
    String dbNamespace = dbInfo.getDbNamespace();

    StringBuilder poolName = new StringBuilder();
    if (serverAddress != null) {
      if (serverAddress.indexOf(':') >= 0) {
        poolName.append('[').append(serverAddress).append(']');
      } else {
        poolName.append(serverAddress);
      }
      if (serverPort != null) {
        poolName.append(':').append(serverPort);
      }
    }
    if (dbNamespace != null) {
      if (poolName.length() > 0) {
        poolName.append('/');
      }
      poolName.append(dbNamespace);
    }

    return poolName.length() > 0 ? poolName.toString() : "alibaba-druid";
  }

  public static DruidTelemetry telemetry() {
    return telemetry;
  }

  private DruidSingletons() {}
}
