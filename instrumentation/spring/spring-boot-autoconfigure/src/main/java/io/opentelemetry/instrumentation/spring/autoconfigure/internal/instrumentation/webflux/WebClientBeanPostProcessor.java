/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.SpringWebfluxBuilderUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
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
  private final ObjectProvider<ConfigProperties> configPropertiesProvider;

  WebClientBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.configPropertiesProvider = configPropertiesProvider;
  }

  static SpringWebfluxTelemetry getWebfluxTelemetry(
      OpenTelemetry openTelemetry, ConfigProperties config) {
    return InstrumentationConfigUtil.configureClientAndServerBuilder(
            config,
            SpringWebfluxTelemetry.builder(openTelemetry),
            SpringWebfluxBuilderUtil.getClientBuilderExtractor(),
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
    SpringWebfluxTelemetry instrumentation =
        getWebfluxTelemetry(
            openTelemetryProvider.getObject(), configPropertiesProvider.getObject());
    return webClientBuilder.filters(instrumentation::addClientTracingFilter);
  }
}
