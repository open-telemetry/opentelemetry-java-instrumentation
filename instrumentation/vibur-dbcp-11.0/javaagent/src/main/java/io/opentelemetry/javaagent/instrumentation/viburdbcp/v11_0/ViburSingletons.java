/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.viburdbcp.v11_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.instrumentation.viburdbcp.v11_0.ViburTelemetry;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import javax.annotation.Nullable;
import org.vibur.dbcp.ViburConfig;
import org.vibur.dbcp.ViburDBCPDataSource;

public class ViburSingletons {

  private static final String DEFAULT_DATA_SOURCE_NAME = "vibur-dbcp";
  private static final VirtualField<ViburConfig, Boolean> CONFIGURED_NAME_FIELD =
      VirtualField.find(ViburConfig.class, Boolean.class);

  private static final ViburTelemetry telemetry = ViburTelemetry.create(GlobalOpenTelemetry.get());

  public static ViburTelemetry telemetry() {
    return telemetry;
  }

  public static void markDataSourceNameConfigured(ViburConfig config, @Nullable String name) {
    // Vibur rejects invalid names and does not support renaming, so this state is monotonic.
    if (name != null && !name.trim().isEmpty()) {
      CONFIGURED_NAME_FIELD.set(config, true);
    }
  }

  public static boolean isDataSourceNameConfigured(ViburConfig config) {
    return Boolean.TRUE.equals(CONFIGURED_NAME_FIELD.get(config));
  }

  public static void registerMetrics(ViburDBCPDataSource dataSource) {
    String poolName = isDataSourceNameConfigured(dataSource) ? dataSource.getName() : null;
    DbInfo dbInfo = getDbInfo(dataSource);
    telemetry()
        .registerMetrics(
            dataSource,
            JdbcConnectionPoolMetricsUtil.poolName(dbInfo, poolName, DEFAULT_DATA_SOURCE_NAME),
            JdbcConnectionPoolMetricsUtil.databaseAttributes(dbInfo));
  }

  private static DbInfo getDbInfo(ViburDBCPDataSource dataSource) {
    return JdbcConnectionUrlParser.parse(dataSource.getJdbcUrl(), dataSource.getDriverProperties());
  }

  private ViburSingletons() {}
}
