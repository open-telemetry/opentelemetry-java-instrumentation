/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.sql.DataSource;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

final class DataSourcePostProcessor implements BeanPostProcessor, Ordered {

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
  private final ObjectProvider<ConfigProperties> configPropertiesProvider;

  DataSourcePostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.configPropertiesProvider = configPropertiesProvider;
  }

  @CanIgnoreReturnValue
  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    // Exclude scoped proxy beans to avoid double wrapping
    if (bean instanceof DataSource && !ScopedProxyUtils.isScopedTarget(beanName)) {
      DataSource dataSource = (DataSource) bean;
      return JdbcTelemetry.builder(openTelemetryProvider.getObject())
          .setStatementSanitizationEnabled(
              InstrumentationConfigUtil.isStatementSanitizationEnabled(
                  configPropertiesProvider.getObject(),
                  "otel.instrumentation.jdbc.statement-sanitizer.enabled"))
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
