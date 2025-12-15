/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractMicrometerBridgeAutoConfigurationTest;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;

class MicrometerBridgeAutoConfigurationTest extends AbstractMicrometerBridgeAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(MicrometerBridgeSpringBoot4AutoConfiguration.class);
  }

  @Override
  protected Class<?> getMetricsAutoConfigurationClass() {
    return MetricsAutoConfiguration.class;
  }

  @Override
  protected Class<?> getMeterRegistryClass() {
    return MeterRegistry.class;
  }
}
