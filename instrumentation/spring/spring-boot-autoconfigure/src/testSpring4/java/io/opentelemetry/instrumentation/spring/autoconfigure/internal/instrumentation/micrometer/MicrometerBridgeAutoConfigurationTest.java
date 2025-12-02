/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractMicrometerBridgeAutoConfigurationTest;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MicrometerBridgeAutoConfigurationTest extends AbstractMicrometerBridgeAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(
              AutoConfigurations.of(MicrometerBridgeSpringBoot4AutoConfiguration.class));

  protected ApplicationContextRunner contextRunner() {
    return runner;
  }

  protected Class<?> getMetricsAutoConfigurationClass() {
    return MetricsAutoConfiguration.class;
  }

  protected Class<?> getMeterRegistryClass() {
    return MeterRegistry.class;
  }
}
