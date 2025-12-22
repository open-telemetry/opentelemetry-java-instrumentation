/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxClientTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxServerTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.SpringWebfluxBuilderUtil;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientTracingFilter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
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

  static SpringWebfluxClientTelemetry getWebfluxClientTelemetry(OpenTelemetry openTelemetry) {
    return InstrumentationConfigUtil.configureClientBuilder(
            openTelemetry,
            SpringWebfluxClientTelemetry.builder(openTelemetry),
            SpringWebfluxBuilderUtil.getClientBuilderExtractor())
        .build();
  }

  static SpringWebfluxServerTelemetry getWebfluxServerTelemetry(OpenTelemetry openTelemetry) {
    return InstrumentationConfigUtil.configureServerBuilder(
            openTelemetry,
            SpringWebfluxServerTelemetry.builder(openTelemetry),
            SpringWebfluxBuilderUtil.getServerBuilderExtractor())
        .build();
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof WebClient) {
      return addWebClientFilterIfNotPresent((WebClient) bean, openTelemetryProvider.getObject());
    }
    return bean;
  }

  private static WebClient addWebClientFilterIfNotPresent(
      WebClient webClient, OpenTelemetry openTelemetry) {
    AtomicBoolean filterAdded = new AtomicBoolean(false);
    WebClient.Builder builder =
        webClient
            .mutate()
            .filters(
                filters -> {
                  if (isFilterNotPresent(filters)) {
                    getWebfluxClientTelemetry(openTelemetry).addFilter(filters);
                    filterAdded.set(true);
                  }
                });

    return filterAdded.get() ? builder.build() : webClient;
  }

  private static boolean isFilterNotPresent(List<ExchangeFilterFunction> filters) {
    return filters.stream().noneMatch(WebClientTracingFilter.class::isInstance);
  }
}
