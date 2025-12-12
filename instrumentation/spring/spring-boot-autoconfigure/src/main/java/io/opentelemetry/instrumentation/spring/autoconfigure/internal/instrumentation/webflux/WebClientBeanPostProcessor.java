/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxClientTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxServerTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.SpringWebfluxBuilderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Inspired by <a
 * href="https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/instrument/web/client/TraceWebClientAutoConfiguration.java">Spring
 * Cloud Sleuth</a>.
 */
final class WebClientBeanPostProcessor implements BeanPostProcessor {

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
  private final ObjectProvider<InstrumentationConfig> configProvider;

  WebClientBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<InstrumentationConfig> configProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.configProvider = configProvider;
  }

  static SpringWebfluxClientTelemetry getWebfluxClientTelemetry(
      OpenTelemetry openTelemetry, InstrumentationConfig config) {
    return InstrumentationConfigUtil.configureClientBuilder(
            openTelemetry,
            config,
            SpringWebfluxClientTelemetry.builder(openTelemetry),
            SpringWebfluxBuilderUtil.getClientBuilderExtractor())
        .build();
  }

  static SpringWebfluxServerTelemetry getWebfluxServerTelemetry(
      OpenTelemetry openTelemetry, InstrumentationConfig config) {
    return InstrumentationConfigUtil.configureServerBuilder(
            openTelemetry,
            config,
            SpringWebfluxServerTelemetry.builder(openTelemetry),
            SpringWebfluxBuilderUtil.getServerBuilderExtractor())
        .build();
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof WebClient) {
      WebClient webClient = (WebClient) bean;
      return wrapBuilder(webClient.mutate()).build();
    } else if (bean instanceof WebClient.Builder) {
      WebClient.Builder webClientBuilder = (WebClient.Builder) bean;
      return wrapBuilder(webClientBuilder);
    }
    return bean;
  }

  private WebClient.Builder wrapBuilder(WebClient.Builder webClientBuilder) {
    SpringWebfluxClientTelemetry instrumentation =
        getWebfluxClientTelemetry(openTelemetryProvider.getObject(), configProvider.getObject());
    return webClientBuilder.filters(instrumentation::addFilter);
  }
}
