/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.spring.web.v3_1.SpringWebTelemetry;
import io.opentelemetry.instrumentation.spring.web.v3_1.internal.WebTelemetryUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

final class RestClientBeanPostProcessor implements BeanPostProcessor {

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
  private final ObjectProvider<ConfigProperties> configPropertiesProvider;

  public RestClientBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.configPropertiesProvider = configPropertiesProvider;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof RestClient restClient) {
      return addRestClientInterceptorIfNotPresent(
          restClient, openTelemetryProvider.getObject(), configPropertiesProvider.getObject());
    }
    return bean;
  }

  private static RestClient addRestClientInterceptorIfNotPresent(
      RestClient restClient, OpenTelemetry openTelemetry, ConfigProperties config) {
    ClientHttpRequestInterceptor instrumentationInterceptor = getInterceptor(openTelemetry, config);

    return restClient
        .mutate()
        .requestInterceptors(
            interceptors -> {
              if (interceptors.stream()
                  .noneMatch(
                      interceptor ->
                          interceptor.getClass() == instrumentationInterceptor.getClass())) {
                interceptors.add(0, instrumentationInterceptor);
              }
            })
        .build();
  }

  static ClientHttpRequestInterceptor getInterceptor(
      OpenTelemetry openTelemetry, ConfigProperties config) {
    return InstrumentationConfigUtil.configureClientBuilder(
            config,
            SpringWebTelemetry.builder(openTelemetry),
            WebTelemetryUtil.getBuilderExtractor())
        .build()
        .newInterceptor();
  }
}
