/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures {@link RestClient} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to {@link RestClient} beans after initialization.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "spring-web")
@ConditionalOnClass(RestClient.class)
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@Configuration
public class RestClientInstrumentationAutoConfiguration {

  @Bean
  static RestClientBeanPostProcessor otelRestClientBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    return new RestClientBeanPostProcessor(openTelemetryProvider, configPropertiesProvider);
  }

  @Bean
  RestClientCustomizer otelRestClientCustomizer(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    return builder ->
        builder.requestInterceptor(
            RestClientBeanPostProcessor.getInterceptor(
                openTelemetryProvider.getObject(), configPropertiesProvider.getObject()));
  }
}
