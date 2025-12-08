/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public abstract class AbstractMicrometerBridgeAutoConfigurationTest {

  protected abstract AutoConfigurations autoConfigurations();

  protected abstract Class<?> getMetricsAutoConfigurationClass();

  protected abstract Class<?> getMeterRegistryClass();

  protected final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(autoConfigurations());

  @Test
  void metricsEnabled() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(getMetricsAutoConfigurationClass()))
        .withPropertyValues("otel.instrumentation.micrometer.enabled = true")
        .run(
            context ->
                assertThat(context.getBean("otelMeterRegistry", getMeterRegistryClass()))
                    .isNotNull()
                    .isInstanceOf(OpenTelemetryMeterRegistry.class));
  }

  @Test
  void metricsDisabledByDefault() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(getMetricsAutoConfigurationClass()))
        .run(context -> assertThat(context.containsBean("otelMeterRegistry")).isFalse());
  }

  @Test
  void metricsDisabled() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(getMetricsAutoConfigurationClass()))
        .withPropertyValues("otel.instrumentation.micrometer.enabled = false")
        .run(context -> assertThat(context.containsBean("otelMeterRegistry")).isFalse());
  }

  @Test
  void noActuatorAutoConfiguration() {
    contextRunner
        .withPropertyValues("otel.instrumentation.micrometer.enabled = true")
        .run(context -> assertThat(context.containsBean("otelMeterRegistry")).isFalse());
  }
}
