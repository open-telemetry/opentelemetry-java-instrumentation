/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.r2dbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass(ConnectionFactory.class)
@ConditionalOnEnabledInstrumentation(module = "r2dbc")
@Configuration(proxyBeanMethods = false)
public class R2dbcInstrumentationAutoConfiguration {

  public R2dbcInstrumentationAutoConfiguration() {}

  @Bean
  // static to avoid "is not eligible for getting processed by all BeanPostProcessors" warning
  static R2dbcInstrumentingPostProcessor r2dbcInstrumentingPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    return new R2dbcInstrumentingPostProcessor(openTelemetryProvider, configPropertiesProvider);
  }
}
