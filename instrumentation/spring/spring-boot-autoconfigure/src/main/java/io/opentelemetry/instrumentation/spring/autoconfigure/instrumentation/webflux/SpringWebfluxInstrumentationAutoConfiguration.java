/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.webflux;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
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
@Configuration
public class SpringWebfluxInstrumentationAutoConfiguration {

  @Bean
  WebClientBeanPostProcessor otelWebClientBeanPostProcessor(OpenTelemetry openTelemetry) {
    return new WebClientBeanPostProcessor(openTelemetry);
  }
}
