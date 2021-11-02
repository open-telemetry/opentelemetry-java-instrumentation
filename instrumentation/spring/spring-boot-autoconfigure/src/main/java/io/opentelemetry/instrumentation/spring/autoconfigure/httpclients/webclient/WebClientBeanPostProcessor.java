/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.webclient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.client.SpringWebfluxTracing;
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

  WebClientBeanPostProcessor(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof WebClient) {
      WebClient webClient = (WebClient) bean;
      return wrapBuilder(openTelemetryProvider, webClient.mutate()).build();
    } else if (bean instanceof WebClient.Builder) {
      WebClient.Builder webClientBuilder = (WebClient.Builder) bean;
      return wrapBuilder(openTelemetryProvider, webClientBuilder);
    }
    return bean;
  }

  private static WebClient.Builder wrapBuilder(
      ObjectProvider<OpenTelemetry> openTelemetryProvider, WebClient.Builder webClientBuilder) {

    OpenTelemetry openTelemetry = openTelemetryProvider.getIfUnique();
    if (openTelemetry != null) {
      SpringWebfluxTracing instrumentation = SpringWebfluxTracing.create(openTelemetry);
      return webClientBuilder.filters(instrumentation::addClientTracingFilter);
    } else {
      return webClientBuilder;
    }
  }
}
