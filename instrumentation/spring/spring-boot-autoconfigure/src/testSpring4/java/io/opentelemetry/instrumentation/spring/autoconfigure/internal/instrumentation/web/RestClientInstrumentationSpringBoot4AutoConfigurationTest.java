/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractRestClientInstrumentationAutoConfigurationTest;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.http.client.ClientHttpRequestInterceptor;

class RestClientInstrumentationSpringBoot4AutoConfigurationTest
    extends AbstractRestClientInstrumentationAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(RestClientInstrumentationSpringBoot4AutoConfiguration.class);
  }

  @Override
  protected Class<?> postProcessorClass() {
    return RestClientBeanPostProcessorSpring4.class;
  }

  @Override
  protected ClientHttpRequestInterceptor getInterceptor(
      OpenTelemetry openTelemetry, InstrumentationConfig config) {
    return RestClientBeanPostProcessorSpring4.getInterceptor(openTelemetry, config);
  }
}
