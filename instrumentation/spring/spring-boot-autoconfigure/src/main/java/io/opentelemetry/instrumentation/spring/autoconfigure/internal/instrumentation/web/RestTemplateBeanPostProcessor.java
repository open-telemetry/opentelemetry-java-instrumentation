/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.client.RestTemplate;

final class RestTemplateBeanPostProcessor implements BeanPostProcessor {

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;

  private final ObjectProvider<ConfigProperties> configPropertiesProvider;

  RestTemplateBeanPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.configPropertiesProvider = configPropertiesProvider;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!(bean instanceof RestTemplate)) {
      return bean;
    }

    return RestTemplateInstrumentation.addIfNotPresent(
        (RestTemplate) bean,
        openTelemetryProvider.getObject(),
        configPropertiesProvider.getObject());
  }
}
