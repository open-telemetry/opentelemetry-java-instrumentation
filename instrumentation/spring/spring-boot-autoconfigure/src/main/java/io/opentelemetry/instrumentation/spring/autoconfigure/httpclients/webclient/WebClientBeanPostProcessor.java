/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.webclient;

import io.opentelemetry.instrumentation.spring.webflux.client.WebClientTracingFilter;
import io.opentelemetry.trace.Tracer;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Inspired by: <a
 * href="https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/instrument/web/client/TraceWebClientAutoConfiguration.java">Spring
 * Cloud Sleuth</a>
 */
final class WebClientBeanPostProcessor implements BeanPostProcessor {

  private final Tracer tracer;

  WebClientBeanPostProcessor(Tracer tracer) {
    this.tracer = tracer;
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
    return webClientBuilder.filters(webClientFilterFunctionConsumer());
  }

  private Consumer<List<ExchangeFilterFunction>> webClientFilterFunctionConsumer() {
    return functions -> {
      if (functions.stream().noneMatch(filter -> filter instanceof WebClientTracingFilter)) {
        WebClientTracingFilter.addFilter(functions);
      }
    };
  }
}
