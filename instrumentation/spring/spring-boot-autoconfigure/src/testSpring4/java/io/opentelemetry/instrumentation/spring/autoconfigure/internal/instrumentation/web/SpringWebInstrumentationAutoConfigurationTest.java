/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractSpringWebInstrumentationAutoConfigurationTest;
import org.springframework.boot.autoconfigure.AutoConfigurations;

class SpringWebInstrumentationAutoConfigurationTest
    extends AbstractSpringWebInstrumentationAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(SpringWebInstrumentationSpringBoot4AutoConfiguration.class);
  }
}
