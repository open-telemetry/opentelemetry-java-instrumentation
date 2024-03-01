/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Configures {@link RestTemplate} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to RestTemplate beans after initialization
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(name = "otel.instrumentation.spring-web.enabled", matchIfMissing = true)
@Conditional(SdkEnabled.class)
@Configuration
public class SpringWebInstrumentationAutoConfiguration {

  public SpringWebInstrumentationAutoConfiguration() {}

  @ConditionalOnClass(RestTemplate.class)
  @Bean
  static RestTemplateBeanPostProcessor otelRestTemplateBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new RestTemplateBeanPostProcessor(openTelemetryProvider);
  }

  @ConditionalOnClass(RestClient.class)
  @Bean
  static RestClientBeanPostProcessor otelRestClientBeanPostProcessor(
    ObjectProvider<OpenTelemetry> openTelemetryProvider) {
      return new RestClientBeanPostProcessor(openTelemetryProvider);
  }
}
