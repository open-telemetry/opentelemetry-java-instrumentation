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
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsUtil;
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

  public static void registerMetrics(AbstractPoolBackedDataSource dataSource) {
    String poolName = dataSource.getDataSourceName();
    if (poolName != null && poolName.equals(dataSource.getIdentityToken())) {
      poolName = null;
    }

    DbInfo dbInfo = getDbInfo(dataSource);
    telemetry()
        .registerMetrics(
            dataSource,
            JdbcConnectionPoolMetricsUtil.poolName(dbInfo, poolName, DEFAULT_DATA_SOURCE_NAME),
            JdbcConnectionPoolMetricsUtil.databaseAttributes(dbInfo));
  }

  private static DbInfo getDbInfo(AbstractPoolBackedDataSource dataSource) {
    ConnectionPoolDataSource poolDataSource = dataSource.getConnectionPoolDataSource();
    if (!(poolDataSource instanceof WrapperConnectionPoolDataSource)) {
      return DbInfo.DEFAULT;
    }

    DataSource nestedDataSource =
        ((WrapperConnectionPoolDataSource) poolDataSource).getNestedDataSource();
    if (!(nestedDataSource instanceof DriverManagerDataSource)) {
      return DbInfo.DEFAULT;
    }

    DriverManagerDataSource driverManagerDataSource = (DriverManagerDataSource) nestedDataSource;
    return JdbcConnectionUrlParser.parse(
        driverManagerDataSource.getJdbcUrl(), driverManagerDataSource.getProperties());
  }

  private C3p0Singletons() {}
}
