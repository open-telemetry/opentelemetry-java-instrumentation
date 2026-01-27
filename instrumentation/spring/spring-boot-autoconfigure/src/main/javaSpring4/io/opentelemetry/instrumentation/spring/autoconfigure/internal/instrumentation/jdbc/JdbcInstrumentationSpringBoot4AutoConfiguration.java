/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnEnabledInstrumentation(module = "jdbc")
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean({DataSource.class})
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(DataSourceAutoConfiguration.class)
public class JdbcInstrumentationSpringBoot4AutoConfiguration {

  // For error prone
  public JdbcInstrumentationSpringBoot4AutoConfiguration() {}

  @Bean
  // static to avoid "is not eligible for getting processed by all BeanPostProcessors" warning
  static DataSourcePostProcessor dataSourcePostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new DataSourcePostProcessor(openTelemetryProvider);
  }
}
