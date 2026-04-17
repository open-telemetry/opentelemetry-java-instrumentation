/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxClientTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures {@link WebClient} for tracing.
 *
 * <p>Adds OpenTelemetry instrumentation via WebClientCustomizer for Spring boot 4.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "spring-webflux")
@ConditionalOnClass({WebClient.class, WebClientCustomizer.class})
@Configuration
public class SpringWebClientInstrumentationSpringBoot4AutoConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 10)
  WebClientCustomizer otelWebClientCustomizer(OpenTelemetry openTelemetry) {
    SpringWebfluxClientTelemetry webfluxClientTelemetry =
        WebClientBeanPostProcessor.getWebfluxClientTelemetry(openTelemetry);
    return builder -> builder.filters(webfluxClientTelemetry::addFilter);
  }
}
