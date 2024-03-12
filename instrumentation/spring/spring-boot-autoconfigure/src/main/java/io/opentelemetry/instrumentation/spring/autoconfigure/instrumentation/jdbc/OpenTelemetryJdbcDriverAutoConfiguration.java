/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryInjector;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@ConditionalOnClass(OpenTelemetryDriver.class)
@ConditionalOnProperty(
    name = "spring.datasource.driver-class-name",
    havingValue = "io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver")
@Conditional(SdkEnabled.class)
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(
    name = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnBean(name = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
public class OpenTelemetryJdbcDriverAutoConfiguration {
  @Bean
  OpenTelemetryInjector injectOtelIntoJdbcDriver() {
    return openTelemetry -> OpenTelemetryDriver.install(openTelemetry);
  }

  // To be sure OpenTelemetryDriver knows the OpenTelemetry bean before the initialization of the
  // database connection pool
  // See org.springframework.boot.autoconfigure.jdbc.DataSourceConfiguration and
  // io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration
  @Bean
  BeanFactoryPostProcessor openTelemetryBeanCreatedBeforeDatasourceBean() {
    return configurableBeanFactory -> {
      BeanDefinition dataSourceBean = configurableBeanFactory.getBeanDefinition("dataSource");
      dataSourceBean.setDependsOn("openTelemetry");
    };
  }
}
