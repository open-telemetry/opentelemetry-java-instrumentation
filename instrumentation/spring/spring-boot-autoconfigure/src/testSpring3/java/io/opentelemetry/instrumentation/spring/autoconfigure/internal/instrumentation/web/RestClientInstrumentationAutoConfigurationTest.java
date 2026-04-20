/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractRestClientInstrumentationAutoConfigurationTest;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.http.client.ClientHttpRequestInterceptor;

class RestClientInstrumentationAutoConfigurationTest
    extends AbstractRestClientInstrumentationAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(RestClientInstrumentationAutoConfiguration.class);
  }

  @Override
  protected Class<RestClientBeanPostProcessor> postProcessorClass() {
    return RestClientBeanPostProcessor.class;
  }

  @Override
  protected ClientHttpRequestInterceptor getInterceptor(OpenTelemetry openTelemetry) {
    return RestClientBeanPostProcessor.getInterceptor(openTelemetry);
  }
}
