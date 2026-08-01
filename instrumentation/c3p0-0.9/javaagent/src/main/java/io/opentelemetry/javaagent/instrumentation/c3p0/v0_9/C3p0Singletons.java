/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.c3p0.v0_9;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.impl.AbstractPoolBackedDataSource;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.c3p0.v0_9.C3p0Telemetry;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;

public class C3p0Singletons {

  private static final String DEFAULT_DATA_SOURCE_NAME = "c3p0";
  private static final C3p0Telemetry telemetry = C3p0Telemetry.create(GlobalOpenTelemetry.get());

  public static C3p0Telemetry telemetry() {
    return telemetry;
  }

  public static String getDataSourceName(AbstractPoolBackedDataSource dataSource) {
    ConnectionPoolDataSource poolDataSource = dataSource.getConnectionPoolDataSource();
    if (!(poolDataSource instanceof WrapperConnectionPoolDataSource)) {
      return DEFAULT_DATA_SOURCE_NAME;
    }

    DataSource nestedDataSource =
        ((WrapperConnectionPoolDataSource) poolDataSource).getNestedDataSource();
    if (!(nestedDataSource instanceof DriverManagerDataSource)) {
      return DEFAULT_DATA_SOURCE_NAME;
    }

    DriverManagerDataSource driverManagerDataSource = (DriverManagerDataSource) nestedDataSource;
    DbInfo dbInfo =
        JdbcConnectionUrlParser.parse(
            driverManagerDataSource.getJdbcUrl(), driverManagerDataSource.getProperties());

    String serverAddress = dbInfo.getServerAddress();
    Integer serverPort = dbInfo.getServerPort();
    String dbNamespace =
        dbInfo.getDbNamespace() != null ? dbInfo.getDbNamespace() : dbInfo.getDbName();

    StringBuilder result = new StringBuilder();
    if (serverAddress != null) {
      result.append(serverAddress);
      if (serverPort != null) {
        result.append(':').append(serverPort);
      }
    }
    if (dbNamespace != null) {
      if (result.length() > 0) {
        result.append('/');
      }
      result.append(dbNamespace);
    }

    return result.length() == 0 ? DEFAULT_DATA_SOURCE_NAME : result.toString();
  }

  private C3p0Singletons() {}
}
