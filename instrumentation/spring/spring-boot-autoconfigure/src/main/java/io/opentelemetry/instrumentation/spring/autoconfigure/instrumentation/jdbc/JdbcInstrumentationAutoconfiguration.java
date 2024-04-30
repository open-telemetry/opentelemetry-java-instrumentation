/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.jdbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(name = "otel.instrumentation.jdbc.enabled", matchIfMissing = true)
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean({DataSource.class})
@Conditional(SdkEnabled.class)
@Configuration(proxyBeanMethods = false)
public class JdbcInstrumentationAutoconfiguration {

  // For error prone
  public JdbcInstrumentationAutoconfiguration() {}

  @Bean
  // static to avoid "is not eligible for getting processed by all BeanPostProcessors" warning
  static DataSourcePostProcessor dataSourcePostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new DataSourcePostProcessor(openTelemetryProvider);
  }
}
