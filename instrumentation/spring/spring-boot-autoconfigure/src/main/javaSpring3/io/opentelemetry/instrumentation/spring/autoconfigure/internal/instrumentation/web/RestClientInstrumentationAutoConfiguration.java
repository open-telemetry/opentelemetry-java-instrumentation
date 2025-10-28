/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
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
 * <p>Adds OpenTelemetry instrumentation to {@link RestClient} beans after initialization.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "spring-web")
@ConditionalOnClass({RestClient.class, RestClientCustomizer.class})
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@Configuration
public class RestClientInstrumentationAutoConfiguration {

  @Bean
  static RestClientBeanPostProcessor otelRestClientBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<InstrumentationConfig> configProvider) {
    return new RestClientBeanPostProcessor(openTelemetryProvider, configProvider);
  }

  @Bean
  RestClientCustomizer otelRestClientCustomizer(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<InstrumentationConfig> configProvider) {
    return builder ->
        builder.requestInterceptor(
            RestClientBeanPostProcessor.getInterceptor(
                openTelemetryProvider.getObject(), configProvider.getObject()));
  }
}
