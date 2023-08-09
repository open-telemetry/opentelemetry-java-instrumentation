/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
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
@Configuration
public class SpringWebInstrumentationAutoConfiguration {

  @Bean
  RestTemplateBeanPostProcessor otelRestTemplateBeanPostProcessor(OpenTelemetry openTelemetry) {
    return new RestTemplateBeanPostProcessor(openTelemetry);
  }
}
