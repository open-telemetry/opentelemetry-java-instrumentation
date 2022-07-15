/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.micrometer1shim.OpenTelemetryMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MicrometerShimAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, MicrometerShimAutoConfiguration.class));

  @Test
  void metricsEnabled() {
    runner
        .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class))
        .withPropertyValues("otel.springboot.micrometer.enabled = true")
        .run(
            context ->
                assertThat(context.getBean("micrometerShim", MeterRegistry.class))
                    .isNotNull()
                    .isInstanceOf(OpenTelemetryMeterRegistry.class));
  }

  @Test
  void metricsEnabledByDefault() {
    runner
        .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class))
        .run(
            context ->
                assertThat(context.getBean("micrometerShim", MeterRegistry.class))
                    .isNotNull()
                    .isInstanceOf(OpenTelemetryMeterRegistry.class));
  }

  @Test
  void metricsDisabled() {
    runner
        .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class))
        .withPropertyValues("otel.springboot.micrometer.enabled = false")
        .run(context -> assertThat(context.containsBean("micrometerShim")).isFalse());
  }

  @Test
  void noActuatorAutoConfiguration() {
    runner
        .withPropertyValues("otel.springboot.micrometer.enabled = true")
        .run(context -> assertThat(context.containsBean("micrometerShim")).isFalse());
  }
}
