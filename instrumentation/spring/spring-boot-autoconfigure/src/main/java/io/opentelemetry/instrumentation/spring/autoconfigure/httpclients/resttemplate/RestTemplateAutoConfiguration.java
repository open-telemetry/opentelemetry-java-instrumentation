/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.resttemplate;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.HttpClientsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configures {@link RestTemplate} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to RestTemplate beans after initialization
 */
@Configuration
@ConditionalOnClass(RestTemplate.class)
@EnableConfigurationProperties(HttpClientsProperties.class)
@ConditionalOnProperty(
    prefix = "otel.springboot.httpclients",
    name = "enabled",
    matchIfMissing = true)
public class RestTemplateAutoConfiguration {

  @Bean
  public RestTemplateBeanPostProcessor otelRestTemplateBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new RestTemplateBeanPostProcessor(openTelemetryProvider);
  }
}
