/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configures {@link RestTemplate} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to RestTemplate beans after initialization.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "spring-web")
@ConditionalOnClass(RestTemplate.class)
@Configuration
public class SpringWebInstrumentationAutoConfiguration {

  public SpringWebInstrumentationAutoConfiguration() {}

  // static to avoid "is not eligible for getting processed by all BeanPostProcessors" warning
  @Bean
  static RestTemplateBeanPostProcessor otelRestTemplateBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    return new RestTemplateBeanPostProcessor(openTelemetryProvider, configPropertiesProvider);
  }

  @Bean
  RestTemplateCustomizer otelRestTemplateCustomizer(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    return restTemplate ->
        RestTemplateInstrumentation.addIfNotPresent(
            restTemplate, openTelemetryProvider.getObject(), configPropertiesProvider.getObject());
  }
}
