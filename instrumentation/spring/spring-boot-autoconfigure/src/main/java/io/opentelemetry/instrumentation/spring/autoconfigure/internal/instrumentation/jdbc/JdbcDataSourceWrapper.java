/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.sql.DataSource;

final class JdbcDataSourceWrapper {
  private JdbcDataSourceWrapper() {}

  private static final Class<?> ROUTING_DATA_SOURCE_CLASS = getRoutingDataSourceClass();

  private static Class<?> getRoutingDataSourceClass() {
    try {
      return Class.forName("org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource");
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  static boolean isRoutingDatasource(Object bean) {
    return ROUTING_DATA_SOURCE_CLASS != null && ROUTING_DATA_SOURCE_CLASS.isInstance(bean);
  }

  static DataSource wrapIfNecessary(
      DataSource dataSource,
      OpenTelemetry openTelemetry,
      ConfigProperties configProperties) {
    if (!isRoutingDatasource(dataSource)) {
      return JdbcTelemetry.builder(openTelemetry)
          .setStatementSanitizationEnabled(
              InstrumentationConfigUtil.isStatementSanitizationEnabled(
                  configProperties,
                  "otel.instrumentation.jdbc.statement-sanitizer.enabled"))
          .setCaptureQueryParameters(
              configProperties.getBoolean(
                  "otel.instrumentation.jdbc.experimental.capture-query-parameters", false))
          .setTransactionInstrumenterEnabled(
              configProperties.getBoolean(
                  "otel.instrumentation.jdbc.experimental.transaction.enabled", false))
          .build()
          .wrap(dataSource);
    }
    return dataSource;
  }
} 