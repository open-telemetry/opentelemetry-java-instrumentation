/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractMicrometerBridgeAutoConfigurationTest;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;

class MicrometerBridgeAutoConfigurationTest extends AbstractMicrometerBridgeAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(MicrometerBridgeAutoConfiguration.class);
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
