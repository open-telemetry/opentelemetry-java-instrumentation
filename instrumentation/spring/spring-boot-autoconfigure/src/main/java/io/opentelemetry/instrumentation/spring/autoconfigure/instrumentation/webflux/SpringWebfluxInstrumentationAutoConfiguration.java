/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.webflux;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures {@link WebClient} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to WebClient beans after initialization
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass(WebClient.class)
@ConditionalOnProperty(name = "otel.instrumentation.spring-webflux.enabled", matchIfMissing = true)
@Conditional(SdkEnabled.class)
@Configuration
public class SpringWebfluxInstrumentationAutoConfiguration {

  public SpringWebfluxInstrumentationAutoConfiguration() {}

  @Bean
  static WebClientBeanPostProcessor otelWebClientBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new WebClientBeanPostProcessor(openTelemetryProvider);
  }
}
