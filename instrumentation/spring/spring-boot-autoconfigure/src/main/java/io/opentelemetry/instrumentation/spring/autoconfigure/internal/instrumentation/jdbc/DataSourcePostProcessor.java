/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import javax.sql.DataSource;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

final class DataSourcePostProcessor implements BeanPostProcessor, Ordered {

  private static final Class<?> ROUTING_DATA_SOURCE_CLASS = getRoutingDataSourceClass();

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
  private final ObjectProvider<InstrumentationConfig> configProvider;

  DataSourcePostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<InstrumentationConfig> configProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.configProvider = configProvider;
  }

  private static Class<?> getRoutingDataSourceClass() {
    try {
      return Class.forName("org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource");
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  private static boolean isRoutingDatasource(Object bean) {
    return ROUTING_DATA_SOURCE_CLASS != null && ROUTING_DATA_SOURCE_CLASS.isInstance(bean);
  }

  @CanIgnoreReturnValue
  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    // Exclude scoped proxy beans to avoid double wrapping
    if (bean instanceof DataSource
        && !isRoutingDatasource(bean)
        && !ScopedProxyUtils.isScopedTarget(beanName)) {
      DataSource dataSource = (DataSource) bean;
      InstrumentationConfig config = configProvider.getObject();
      return JdbcTelemetry.builder(openTelemetryProvider.getObject())
          .setStatementSanitizationEnabled(
              InstrumentationConfigUtil.isStatementSanitizationEnabled(
                  config, "otel.instrumentation.jdbc.statement-sanitizer.enabled"))
          .setCaptureQueryParameters(
              config.getBoolean(
                  "otel.instrumentation.jdbc.experimental.capture-query-parameters", false))
          .setTransactionInstrumenterEnabled(
              config.getBoolean(
                  "otel.instrumentation.jdbc.experimental.transaction.enabled", false))
          .build()
          .wrap(dataSource);
    }
    return bean;
  }

  // To be one of the first bean post-processors to be executed
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 20;
  }
}
