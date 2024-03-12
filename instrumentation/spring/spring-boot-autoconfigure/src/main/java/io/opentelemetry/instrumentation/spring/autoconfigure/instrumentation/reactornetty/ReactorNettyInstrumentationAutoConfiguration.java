/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.reactornetty;

import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import reactor.netty.http.client.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures {@link HttpClient} for tracing.
 *
 * <p>Adds Open Telemetry instrumentation to WebClient beans after initialization
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass(WebClient.class)
@ConditionalOnProperty(name = "otel.instrumentation.reactor-netty.enabled", matchIfMissing = true)
@Conditional(SdkEnabled.class)
@Configuration
public class ReactorNettyInstrumentationAutoConfiguration {

  public ReactorNettyInstrumentationAutoConfiguration() {}

  @ConditionalOnClass({HttpClient.class, ObservationRegistry.class})
  @Bean
  static ReactorNettyHttpClientInitializingBean reactorNettyHttpClientInitializingBean(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new ReactorNettyHttpClientInitializingBean(openTelemetryProvider.getIfAvailable());
  }

}
