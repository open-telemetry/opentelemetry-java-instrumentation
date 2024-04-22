/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.web.v3_1.SpringWebTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

public final class RestClientBeanPostProcessor implements BeanPostProcessor {

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;

  public RestClientBeanPostProcessor(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof RestClient restClient) {
      return addRestClientInterceptorIfNotPresent(restClient, openTelemetryProvider.getObject());
    }
    return bean;
  }

  private static RestClient addRestClientInterceptorIfNotPresent(
      RestClient restClient, OpenTelemetry openTelemetry) {
    ClientHttpRequestInterceptor instrumentationInterceptor =
        SpringWebTelemetry.create(openTelemetry).newInterceptor();

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
}
