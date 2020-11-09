/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.webclient;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.HttpClientsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures {@link WebClient} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to WebClient beans after initialization
 */
@Configuration
@ConditionalOnClass(WebClient.class)
@EnableConfigurationProperties(HttpClientsProperties.class)
@ConditionalOnProperty(
    prefix = "opentelemetry.trace.httpclients",
    name = "enabled",
    matchIfMissing = true)
public class WebClientAutoConfiguration {

  @Bean
  @Autowired
  public WebClientBeanPostProcessor otelWebClientBeanPostProcessor(Tracer tracer) {
    return new WebClientBeanPostProcessor(tracer);
  }
}
