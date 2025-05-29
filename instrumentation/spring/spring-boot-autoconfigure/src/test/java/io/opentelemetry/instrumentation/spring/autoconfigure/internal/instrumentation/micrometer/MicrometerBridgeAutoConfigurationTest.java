/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MicrometerBridgeAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(AutoConfigurations.of(MicrometerBridgeAutoConfiguration.class));

  @Test
  void metricsEnabled() {
    runner
        .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class))
        .withPropertyValues("otel.instrumentation.micrometer.enabled = true")
        .run(
            context ->
                assertThat(context.getBean("otelMeterRegistry", MeterRegistry.class))
                    .isNotNull()
                    .isInstanceOf(OpenTelemetryMeterRegistry.class));
  }

  @Test
  void metricsDisabledByDefault() {
    runner
        .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class))
        .run(context -> assertThat(context.containsBean("otelMeterRegistry")).isFalse());
  }

  @Test
  void metricsDisabled() {
    runner
        .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class))
        .withPropertyValues("otel.instrumentation.micrometer.enabled = false")
        .run(context -> assertThat(context.containsBean("otelMeterRegistry")).isFalse());
  }

  @Test
  void noActuatorAutoConfiguration() {
    runner
        .withPropertyValues("otel.instrumentation.micrometer.enabled = true")
        .run(context -> assertThat(context.containsBean("otelMeterRegistry")).isFalse());
  }
}
