/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
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

    return RestTemplateInstrumentation.addIfNotPresent(
        (RestTemplate) bean, openTelemetryProvider.getObject());
  }
}
