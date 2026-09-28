/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hikaricp.v3_0;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.hikaricp.v3_0.HikariTelemetry;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import javax.annotation.Nullable;

public class HikariSingletons {

  private static final String DEFAULT_DATA_SOURCE_NAME = "hikaricp";

  private static final VirtualField<HikariConfig, Boolean> GENERATED_POOL_NAME_FIELD =
      VirtualField.find(HikariConfig.class, Boolean.class);

  private static final HikariTelemetry hikariTelemetry =
      HikariTelemetry.create(GlobalOpenTelemetry.get());

  public static void setGeneratedPoolName(HikariConfig config, boolean generatedPoolName) {
    GENERATED_POOL_NAME_FIELD.set(config, generatedPoolName);
  }

  public static void copyGeneratedPoolName(HikariConfig source, HikariConfig target) {
    Boolean generatedPoolName = GENERATED_POOL_NAME_FIELD.get(source);
    if (generatedPoolName != null) {
      GENERATED_POOL_NAME_FIELD.set(target, generatedPoolName);
    }
  }

  public static MetricsTrackerFactory createMetricsTrackerFactory(
      @Nullable MetricsTrackerFactory delegate, HikariConfig config) {
    DbInfo dbInfo = getDbInfo(config);
    Attributes databaseAttributes = JdbcConnectionPoolMetricsUtil.databaseAttributes(dbInfo);
    if (!Boolean.TRUE.equals(GENERATED_POOL_NAME_FIELD.get(config))) {
      return hikariTelemetry.createMetricsTrackerFactory(delegate, databaseAttributes);
    }

    String poolName =
        JdbcConnectionPoolMetricsUtil.poolName(dbInfo, null, DEFAULT_DATA_SOURCE_NAME);
    return (hikariPoolName, poolStats) ->
        hikariTelemetry
            .createMetricsTrackerFactory(
                delegate == null
                    ? null
                    : (ignored, stats) -> delegate.create(hikariPoolName, stats),
                databaseAttributes)
            .create(poolName, poolStats);
  }

  private static DbInfo getDbInfo(HikariConfig config) {
    if (config.getDataSource() == null && config.getDataSourceClassName() != null) {
      return JdbcConnectionPoolMetricsUtil.dbInfo(config.getDataSourceProperties());
    }

    return JdbcConnectionUrlParser.parse(config.getJdbcUrl(), config.getDataSourceProperties());
  }

  private HikariSingletons() {}
}
