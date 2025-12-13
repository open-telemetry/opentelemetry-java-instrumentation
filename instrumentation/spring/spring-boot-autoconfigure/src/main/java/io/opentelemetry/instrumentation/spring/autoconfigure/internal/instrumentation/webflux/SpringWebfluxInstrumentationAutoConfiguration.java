/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;

/**
 * Configures {@link WebClient} for tracing.
 *
 * <p>Adds OpenTelemetry instrumentation to WebClient beans after initialization.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "spring-webflux")
@ConditionalOnClass(WebClient.class)
@Configuration
public class SpringWebfluxInstrumentationAutoConfiguration {

  public SpringWebfluxInstrumentationAutoConfiguration() {}

  // static to avoid "is not eligible for getting processed by all BeanPostProcessors" warning
  @Bean
  static WebClientBeanPostProcessor otelWebClientBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new WebClientBeanPostProcessor(openTelemetryProvider);
  }

  @Bean
  WebFilter telemetryFilter(OpenTelemetry openTelemetry) {
    return WebClientBeanPostProcessor.getWebfluxServerTelemetry(openTelemetry)
        .createWebFilterAndRegisterReactorHook();
  }
}
