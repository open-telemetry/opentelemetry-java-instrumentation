/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public abstract class AbstractMicrometerBridgeAutoConfigurationTest {

  protected abstract AutoConfigurations autoConfigurations();

  protected abstract Class<?> getMetricsAutoConfigurationClass();

  protected abstract Class<?> getSimpleMetricsExportAutoConfigurationClass();

  protected abstract Class<?> getCompositeMeterRegistryAutoConfigurationClass();

  protected abstract Class<?> getMeterRegistryClass();

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(autoConfigurations());

  @Test
  void metricsEnabled() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(getMetricsAutoConfigurationClass()))
        .withPropertyValues("otel.instrumentation.micrometer.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelMeterRegistry", getMeterRegistryClass()))
                    .isInstanceOf(OpenTelemetryMeterRegistry.class));
  }

  @Test
  void metricsDisabledByDefault() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(getMetricsAutoConfigurationClass()))
        .run(context -> assertThat(context).doesNotHaveBean("otelMeterRegistry"));
  }

  @Test
  void metricsDisabled() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(getMetricsAutoConfigurationClass()))
        .withPropertyValues("otel.instrumentation.micrometer.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("otelMeterRegistry"));
  }

  @Test
  void noActuatorAutoConfiguration() {
    contextRunner
        .withPropertyValues("otel.instrumentation.micrometer.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean("otelMeterRegistry"));
  }

  @Test
  void doesNotActivateWhenMetricsAutoConfigurationIsMissing() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(getMetricsAutoConfigurationClass()))
        .withBean(Clock.class, () -> Clock.SYSTEM)
        .withPropertyValues("otel.instrumentation.micrometer.enabled=true")
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("otelMeterRegistry"));
  }

  @Test
  void keepsFallbackSimpleMeterRegistry() {
    actuatorContextRunner(OpenTelemetry.noop())
        .run(context -> assertThat(context).hasSingleBean(SimpleMeterRegistry.class));
  }

  @Test
  void keepsMetersVisibleWhenThereIsNoOtherRegistryToReadFrom() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(getMetricsAutoConfigurationClass()))
        .withPropertyValues("otel.instrumentation.micrometer.enabled=true")
        .run(
            context -> {
              // without a fallback registry the OTel registry is the only one, so hiding its meters
              // would make them unreadable instead of readable from somewhere else
              MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
              meterRegistry.counter("test.counter").increment();

              assertThat(meterRegistry).isInstanceOf(OpenTelemetryMeterRegistry.class);
              assertThat(meterRegistry.getMeters()).hasSize(1);
              assertThat(meterRegistry.find("test.counter").counter()).isNotNull();
            });
  }

  // repeated because the composite registry keeps its members in an identity hash set, so a reader
  // that relies on its iteration order only fails intermittently
  @RepeatedTest(5)
  void hidesOpenTelemetryRegistryMetersFromReaders() {
    actuatorContextRunner(OpenTelemetry.noop())
        .run(
            context -> {
              context.getBean(MeterRegistry.class).counter("test.counter").increment(2);

              MeterRegistry otelMeterRegistry =
                  context.getBean("otelMeterRegistry", MeterRegistry.class);
              assertThat(otelMeterRegistry).isInstanceOf(OpenTelemetryMeterRegistry.class);
              assertThat(otelMeterRegistry.getMeters()).isEmpty();
              assertThat(otelMeterRegistry.find("test.counter").counter()).isNull();

              assertThat(context.getBean(SimpleMeterRegistry.class).find("test.counter").counter())
                  .isNotNull();
            });
  }

  @Test
  void exposesMetersAddedByMeterBinders() {
    actuatorContextRunner(OpenTelemetry.noop())
        .withBean(MeterBinder.class, () -> registry -> registry.counter("test.bound"))
        .run(
            context ->
                assertThat(context.getBean(MeterRegistry.class).find("test.bound").counter())
                    .isNotNull());
  }

  @Test
  void exportsMetricsToOpenTelemetry() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .build();

    actuatorContextRunner(openTelemetry)
        .run(
            context -> {
              context.getBean(MeterRegistry.class).counter("test.counter").increment(2);

              assertThat(metricReader.collectAllMetrics())
                  .singleElement()
                  .satisfies(
                      metric ->
                          assertThat(metric)
                              .hasName("test.counter")
                              .hasDoubleSumSatisfying(
                                  sum -> sum.hasPointsSatisfying(point -> point.hasValue(2))));
            });
  }

  /**
   * Returns a runner with the bridge enabled and the actuator auto-configurations that an
   * application relying on the actuator metrics endpoint would have.
   */
  protected ApplicationContextRunner actuatorContextRunner(OpenTelemetry openTelemetry) {
    return new ApplicationContextRunner()
        .withBean(OpenTelemetry.class, () -> openTelemetry)
        .withConfiguration(autoConfigurations())
        .withConfiguration(
            AutoConfigurations.of(
                getMetricsAutoConfigurationClass(),
                getSimpleMetricsExportAutoConfigurationClass(),
                getCompositeMeterRegistryAutoConfigurationClass()))
        .withPropertyValues("otel.instrumentation.micrometer.enabled=true");
  }
}
