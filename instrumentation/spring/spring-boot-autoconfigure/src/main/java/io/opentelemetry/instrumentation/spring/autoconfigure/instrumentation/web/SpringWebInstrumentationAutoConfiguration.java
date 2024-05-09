/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configures {@link RestTemplate} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to RestTemplate beans after initialization
 */
@ConditionalOnEnabledInstrumentation(module = "spring-web")
@ConditionalOnClass(RestTemplate.class)
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
