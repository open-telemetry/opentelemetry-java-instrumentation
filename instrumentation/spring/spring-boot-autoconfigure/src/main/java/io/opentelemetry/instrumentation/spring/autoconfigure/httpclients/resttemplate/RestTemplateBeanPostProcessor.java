/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.resttemplate;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.web.SpringWebTelemetry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

final class RestTemplateBeanPostProcessor implements BeanPostProcessor {
  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;

  RestTemplateBeanPostProcessor(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!(bean instanceof RestTemplate)) {
      return bean;
    }

    RestTemplate restTemplate = (RestTemplate) bean;
    OpenTelemetry openTelemetry = openTelemetryProvider.getIfUnique();
    if (openTelemetry != null) {
      ClientHttpRequestInterceptor interceptor =
          SpringWebTelemetry.create(openTelemetry).newInterceptor();
      addRestTemplateInterceptorIfNotPresent(restTemplate, interceptor);
    }
    return restTemplate;
  }

  private static void addRestTemplateInterceptorIfNotPresent(
      RestTemplate restTemplate, ClientHttpRequestInterceptor instrumentationInterceptor) {
    List<ClientHttpRequestInterceptor> restTemplateInterceptors = restTemplate.getInterceptors();
    if (restTemplateInterceptors.stream()
        .noneMatch(
            interceptor -> interceptor.getClass() == instrumentationInterceptor.getClass())) {
      restTemplateInterceptors.add(0, instrumentationInterceptor);
    }
  }
}
