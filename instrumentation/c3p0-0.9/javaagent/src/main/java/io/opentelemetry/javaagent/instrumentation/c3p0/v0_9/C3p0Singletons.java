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
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsInfo;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolNameUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import javax.annotation.Nullable;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;

public class C3p0Singletons {

  private static final String DEFAULT_DATA_SOURCE_NAME = "c3p0";
  private static final C3p0Telemetry telemetry = C3p0Telemetry.create(GlobalOpenTelemetry.get());

  public static C3p0Telemetry telemetry() {
    return telemetry;
  }

  public static JdbcConnectionPoolMetricsInfo getMetricsInfo(
      AbstractPoolBackedDataSource dataSource, @Nullable String poolName) {
    DbInfo dbInfo = getDbInfo(dataSource);
    return poolName == null
        ? JdbcConnectionPoolNameUtil.createMetricsInfo(dbInfo, DEFAULT_DATA_SOURCE_NAME)
        : JdbcConnectionPoolNameUtil.createMetricsInfoWithPoolName(dbInfo, poolName);
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
