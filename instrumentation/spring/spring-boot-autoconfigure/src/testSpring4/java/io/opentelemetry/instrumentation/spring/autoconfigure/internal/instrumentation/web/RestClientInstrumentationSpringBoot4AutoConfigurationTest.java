/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractRestClientInstrumentationAutoConfigurationTest;
import org.springframework.boot.autoconfigure.AutoConfigurations;

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
}
