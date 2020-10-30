/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.resttemplate;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.HttpClientsProperties;
import org.springframework.beans.factory.annotation.Autowired;
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
    prefix = "opentelemetry.trace.httpclients",
    name = "enabled",
    matchIfMissing = true)
public class RestTemplateAutoConfiguration {

  @Bean
  @Autowired
  public RestTemplateBeanPostProcessor otelRestTemplateBeanPostProcessor(Tracer tracer) {
    return new RestTemplateBeanPostProcessor(tracer);
  }
}
