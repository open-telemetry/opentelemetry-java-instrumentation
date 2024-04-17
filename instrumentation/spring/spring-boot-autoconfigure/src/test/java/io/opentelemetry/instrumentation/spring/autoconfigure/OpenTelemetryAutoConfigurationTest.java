/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.internal.OtlpSpanExporterProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.internal.AutoConfigureListener;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto configuration test for {@link OpenTelemetryAutoConfiguration}. */
class OpenTelemetryAutoConfigurationTest {
  @TestConfiguration
  static class CustomTracerConfiguration {
    @Bean
    public OpenTelemetry customOpenTelemetry() {
      return OpenTelemetry.noop();
    }
  }

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none");

  @Test
  @DisplayName(
      "when Application Context contains OpenTelemetry bean should NOT initialize openTelemetry")
  void customOpenTelemetry() {
    this.contextRunner
        .withUserConfiguration(CustomTracerConfiguration.class)
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("customOpenTelemetry")
                    .doesNotHaveBean("openTelemetry"));
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean should initialize openTelemetry")
  void initializeProvidersAndOpenTelemetry() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(context -> assertThat(context).hasBean("openTelemetry"));
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean but SpanExporter should initialize openTelemetry")
  void initializeOpenTelemetryWithCustomProviders() {
    OtlpSpanExporterProvider spanExporterProvider =
        Mockito.mock(
            OtlpSpanExporterProvider.class,
            withSettings().extraInterfaces(AutoConfigureListener.class));
    Mockito.when(spanExporterProvider.getName()).thenReturn("custom");
    Mockito.when(spanExporterProvider.createExporter(any()))
        .thenReturn(Mockito.mock(SpanExporter.class));

    this.contextRunner
        .withBean(
            "customSpanExporter",
            OtlpSpanExporterProvider.class,
            () -> spanExporterProvider,
            bd -> bd.setDestroyMethodName(""))
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .withPropertyValues("otel.traces.exporter=custom")
        .run(context -> assertThat(context).hasBean("openTelemetry"));

    Mockito.verify(spanExporterProvider).afterAutoConfigure(any());
  }

  @Test
  void shouldInitializeSdkWhenNotDisabled() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .withPropertyValues("otel.sdk.disabled=false")
        .run(
            context -> {
              assertThat(context).getBean("openTelemetry").isInstanceOf(OpenTelemetrySdk.class);
              assertThat(context).hasBean("openTelemetry");
            });
  }

  @Test
  void shouldInitializeNoopOpenTelemetryWhenSdkIsDisabled() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .withPropertyValues("otel.sdk.disabled=true")
        .run(
            context ->
                assertThat(context).getBean("openTelemetry").isEqualTo(OpenTelemetry.noop()));
  }
}
