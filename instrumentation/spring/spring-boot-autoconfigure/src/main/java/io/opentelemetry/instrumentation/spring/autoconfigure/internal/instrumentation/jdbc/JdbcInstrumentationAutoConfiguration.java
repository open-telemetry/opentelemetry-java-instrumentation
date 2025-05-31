/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnEnabledInstrumentation(module = "jdbc")
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean({DataSource.class})
@Configuration(proxyBeanMethods = false)
public class JdbcInstrumentationAutoConfiguration {

  // For error prone
  public JdbcInstrumentationAutoConfiguration() {}

  @Bean
  @Primary
  public DataSource otelDataSource(
      @Qualifier("dataSource") DataSource originalDataSource,
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    return JdbcDataSourceWrapper.wrapIfNecessary(
        originalDataSource,
        openTelemetryProvider.getObject(),
        configPropertiesProvider.getObject());
  }
}
