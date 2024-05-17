/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.r2dbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass(ConnectionFactory.class)
@ConditionalOnProperty(name = "otel.instrumentation.r2dbc.enabled", matchIfMissing = true)
@Conditional(SdkEnabled.class)
@Configuration(proxyBeanMethods = false)
public class R2dbcAutoConfiguration {

  public R2dbcAutoConfiguration() {}

  @Bean
  // static to avoid "is not eligible for getting processed by all BeanPostProcessors" warning
  static R2dbcInstrumentingPostProcessor r2dbcInstrumentingPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      @Value("${otel.instrumentation.common.db-statement-sanitizer.enabled:true}")
          boolean statementSanitizationEnabled) {
    return new R2dbcInstrumentingPostProcessor(openTelemetryProvider, statementSanitizationEnabled);
  }
}
