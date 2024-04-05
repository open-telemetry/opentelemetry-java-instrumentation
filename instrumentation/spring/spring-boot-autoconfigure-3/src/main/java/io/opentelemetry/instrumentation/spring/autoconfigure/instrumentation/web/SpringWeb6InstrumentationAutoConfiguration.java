/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import io.opentelemetry.instrumentation.spring.web.v3_1.SpringWebTelemetry;
import io.opentelemetry.testing.internal.armeria.client.RestClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Configures {@link RestClient} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to {@link RestClient} beans after initialization
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(name = "otel.instrumentation.spring-web.enabled", matchIfMissing = true)
@ConditionalOnClass(RestClient.class)
@Conditional(SdkEnabled.class)
@Configuration
public class SpringWeb6InstrumentationAutoConfiguration {

  public SpringWeb6InstrumentationAutoConfiguration() {}

  @Bean
  static RestClientBeanPostProcessor otelRestClientBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new RestClientBeanPostProcessor(openTelemetryProvider);
  }

  @Bean
  static RestClientCustomizer otelRestClientCustomizer(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return builder ->
        builder.requestInterceptor(
            SpringWebTelemetry.create(openTelemetryProvider.getObject()).newInterceptor());
  }
}
