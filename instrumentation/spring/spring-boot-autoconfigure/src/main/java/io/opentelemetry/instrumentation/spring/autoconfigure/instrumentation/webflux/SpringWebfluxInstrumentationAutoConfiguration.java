/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.webflux;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
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
@Conditional(SpringWebfluxInstrumentationAutoConfiguration.Condition.class)
@Configuration
public class SpringWebfluxInstrumentationAutoConfiguration {

  static final class Condition extends AllNestedConditions {
    public Condition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(
        name = "otel.instrumentation.spring-webflux.enabled",
        matchIfMissing = true)
    static class WebFlux {}

    @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "false", matchIfMissing = true)
    static class SdkEnabled {}
  }

  public SpringWebfluxInstrumentationAutoConfiguration() {}

  @Bean
  static WebClientBeanPostProcessor otelWebClientBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new WebClientBeanPostProcessor(openTelemetryProvider);
  }
}
