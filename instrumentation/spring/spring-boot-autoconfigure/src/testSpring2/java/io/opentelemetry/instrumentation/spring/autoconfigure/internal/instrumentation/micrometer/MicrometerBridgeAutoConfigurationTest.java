/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractMicrometerBridgeAutoConfigurationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.redis.LettuceMetricsAutoConfiguration;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
  protected Class<?> getSimpleMetricsExportAutoConfigurationClass() {
    return SimpleMetricsExportAutoConfiguration.class;
  }

  @Override
  protected Class<?> getCompositeMeterRegistryAutoConfigurationClass() {
    return CompositeMeterRegistryAutoConfiguration.class;
  }

  @Override
  protected Class<?> getMeterRegistryClass() {
    return MeterRegistry.class;
  }

  @Test
  void bridgeConfiguredAfterOpenTelemetryWhenWebFluxAndLettuceArePresent() {
    // WebFlux's early order pulls the bridge forward through Session, Redis, Lettuce, and the
    // composite registry, so alphabetical ordering alone cannot put OpenTelemetry first.
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                OpenTelemetryAutoConfiguration.class,
                MicrometerBridgeAutoConfiguration.class,
                MetricsAutoConfiguration.class,
                SimpleMetricsExportAutoConfiguration.class,
                CompositeMeterRegistryAutoConfiguration.class,
                LettuceMetricsAutoConfiguration.class,
                SessionAutoConfiguration.class,
                WebFluxAutoConfiguration.class))
        .withPropertyValues(
            "otel.instrumentation.micrometer.enabled=true",
            "otel.traces.exporter=none",
            "otel.metrics.exporter=none",
            "otel.logs.exporter=none")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(OpenTelemetry.class);
              assertThat(context.getBean("otelMeterRegistry", MeterRegistry.class))
                  .isInstanceOf(OpenTelemetryMeterRegistry.class);
            });
  }

  // repeated because the composite registry keeps its members in an identity hash set, so a reader
  // that relies on its iteration order only fails intermittently
  @RepeatedTest(5)
  void actuatorMetricsEndpointReturnsMeasurements() {
    actuatorContextRunner(OpenTelemetry.noop())
        .run(
            context -> {
              MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
              meterRegistry.counter("test.counter").increment(2);

              // the return type of MetricsEndpoint.metric() differs between Spring Boot 2 and 3
              MetricsEndpoint metricsEndpoint = new MetricsEndpoint(meterRegistry);
              var metric = metricsEndpoint.metric("test.counter", null);
              assertThat(metric).isNotNull();
              assertThat(metric.getMeasurements())
                  .singleElement()
                  .extracting(MetricsEndpoint.Sample::getValue)
                  .isEqualTo(2.0);

              // hiding the OTel registry's meters must not hide them from the name listing, which
              // reads the meters of every composite member
              assertThat(metricsEndpoint.listNames().getNames()).contains("test.counter");
            });
  }
}
