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
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configures {@link RestTemplate} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to RestTemplate beans after initialization
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnProperty(name = "otel.instrumentation.spring-web.enabled", matchIfMissing = true)
@Conditional(SdkEnabled.class)
@Configuration
public class SpringWebInstrumentationAutoConfiguration {

  public SpringWebInstrumentationAutoConfiguration() {}

  // static to avoid "is not eligible for getting processed by all BeanPostProcessors" warning
  @Bean
  static RestTemplateBeanPostProcessor otelRestTemplateBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new RestTemplateBeanPostProcessor(openTelemetryProvider);
  }

  @Bean
  RestTemplateCustomizer otelRestTemplateCustomizer(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return restTemplate ->
        RestTemplateInstrumentation.addIfNotPresent(
            restTemplate, openTelemetryProvider.getObject());
  }
}
