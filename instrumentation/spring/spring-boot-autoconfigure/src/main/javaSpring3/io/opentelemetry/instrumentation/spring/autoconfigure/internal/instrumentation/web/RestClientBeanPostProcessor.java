/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.spring.web.v3_1.SpringWebTelemetry;
import io.opentelemetry.instrumentation.spring.web.v3_1.internal.WebTelemetryUtil;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

final class RestClientBeanPostProcessor implements BeanPostProcessor {

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
    ClientHttpRequestInterceptor instrumentationInterceptor = getInterceptor(openTelemetry);

    AtomicBoolean shouldAddInterceptor = new AtomicBoolean(false);
    RestClient.Builder result =
        restClient
            .mutate()
            .requestInterceptors(
                interceptors -> {
                  if (isInterceptorNotPresent(interceptors, instrumentationInterceptor)) {
                    interceptors.add(0, instrumentationInterceptor);
                    shouldAddInterceptor.set(true);
                  }
                });

    return shouldAddInterceptor.get() ? result.build() : restClient;
  }

  private static boolean isInterceptorNotPresent(
      java.util.List<ClientHttpRequestInterceptor> interceptors,
      ClientHttpRequestInterceptor instrumentationInterceptor) {
    return interceptors.stream()
        .noneMatch(interceptor -> interceptor.getClass() == instrumentationInterceptor.getClass());
  }

  static ClientHttpRequestInterceptor getInterceptor(OpenTelemetry openTelemetry) {
    return InstrumentationConfigUtil.configureClientBuilder(
            openTelemetry,
            SpringWebTelemetry.builder(openTelemetry),
            WebTelemetryUtil.getBuilderExtractor())
        .build()
        .newInterceptor();
  }
}
