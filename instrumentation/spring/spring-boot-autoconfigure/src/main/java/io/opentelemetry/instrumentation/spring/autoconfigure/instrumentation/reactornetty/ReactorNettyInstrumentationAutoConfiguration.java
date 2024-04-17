/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.reactornetty;

import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

/**
 * Configures {@link HttpClient} and {@link reactor.netty.http.server.HttpServer} for tracing.
 *
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(name = "otel.instrumentation.reactor-netty.enabled", matchIfMissing = true)
@Conditional(SdkEnabled.class)
@Configuration
public class ReactorNettyInstrumentationAutoConfiguration {

  public ReactorNettyInstrumentationAutoConfiguration() {}

  @ConditionalOnClass({HttpClient.class, ObservationRegistry.class})
  @Bean
  static ReactorNettyHttpInitializingBean reactorNettyHttpClientInitializingBean(
      ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new ReactorNettyHttpInitializingBean(openTelemetryProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnClass(HttpClient.class)
  static HttpClientBeanPostProcessor httpClientBeanPostProcessor() {
    return new HttpClientBeanPostProcessor();
  }

  @Bean
  @ConditionalOnClass(HttpServer.class)
  static HttpServerBeanPostProcessor httpServerBeanPostProcessor() {
    return new HttpServerBeanPostProcessor();
  }

}
