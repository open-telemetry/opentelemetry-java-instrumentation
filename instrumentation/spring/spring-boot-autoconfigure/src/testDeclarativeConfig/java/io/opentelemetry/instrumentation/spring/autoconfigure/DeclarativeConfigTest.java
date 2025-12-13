/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web.SpringWebInstrumentationAutoConfiguration;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto configuration test for {@link OpenTelemetryAutoConfiguration}. */
class DeclarativeConfigTest {
  @TestConfiguration
  static class CustomTracerConfiguration {
    @Bean
    public OpenTelemetry customOpenTelemetry() {
      return OpenTelemetry.noop();
    }
  }

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
          // to load application.yaml
          .withInitializer(new ConfigDataApplicationContextInitializer());

  @Test
  @DisplayName(
      "when Application Context contains OpenTelemetry bean should NOT initialize openTelemetry")
  void customOpenTelemetry() {
    this.contextRunner
        .withUserConfiguration(CustomTracerConfiguration.class)
        .withPropertyValues("otel.file_format=1.0-rc.1")
        .run(
            context ->
                assertThat(context)
                    .hasBean("customOpenTelemetry")
                    .doesNotHaveBean("openTelemetry")
                    .hasBean("otelProperties"));
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean should initialize openTelemetry")
  void initializeProvidersAndOpenTelemetry() {
    this.contextRunner.run(
        context -> {
          assertThat(context).hasBean("openTelemetry").hasBean("otelProperties");
          OpenTelemetry openTelemetry = context.getBean(OpenTelemetry.class);
          assertThat(DeclarativeConfigUtil.getString(openTelemetry, "foo", "bar"))
              .hasValue("baz");
        });
  }

  @Test
  void shouldInitializeSdkWhenNotDisabled() {
    this.contextRunner
        .withPropertyValues("otel.file_format=1.0-rc.1", "otel.disabled=false")
        .run(
            context ->
                assertThat(context).getBean("openTelemetry").isInstanceOf(OpenTelemetrySdk.class));
  }

  @Test
  void shouldInitializeNoopOpenTelemetryWhenSdkIsDisabled() {
    this.contextRunner
        .withPropertyValues(
            "otel.file_format=1.0-rc.1",
            "otel.disabled=true",
            "otel.resource.attributes=service.name=workflow-backend-dev,service.version=3c8f9ce9")
        .run(
            context ->
                assertThat(context).getBean("openTelemetry").isEqualTo(OpenTelemetry.noop()));
  }

  @Test
  void shouldLoadInstrumentation() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(SpringWebInstrumentationAutoConfiguration.class))
        .withPropertyValues("otel.file_format=1.0-rc.1")
        .run(context -> assertThat(context).hasBean("otelRestTemplateBeanPostProcessor"));
  }

  @Test
  void shouldNotLoadInstrumentationWhenDefaultIsDisabled() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(SpringWebInstrumentationAutoConfiguration.class))
        .withPropertyValues(
            "otel.file_format=1.0-rc.1",
            "otel.instrumentation/development.java.spring_starter.instrumentation_mode=none")
        .run(context -> assertThat(context).doesNotHaveBean("otelRestTemplateBeanPostProcessor"));
  }

  @Test
  void shouldLoadInstrumentationWhenExplicitlyEnabled() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(SpringWebInstrumentationAutoConfiguration.class))
        .withPropertyValues(
            "otel.file_format=1.0-rc.1",
            "otel.instrumentation/development.java.spring_starter.instrumentation_mode=none",
            "otel.instrumentation/development.java.spring_web.enabled=true")
        .run(context -> assertThat(context).hasBean("otelRestTemplateBeanPostProcessor"));
  }

  @Test
  void shouldNotLoadInstrumentationWhenExplicitlyDisabled() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(SpringWebInstrumentationAutoConfiguration.class))
        .withPropertyValues(
            "otel.file_format=1.0-rc.1",
            "otel.instrumentation/development.java.spring_starter.instrumentation_mode=none",
            "otel.instrumentation/development.java.spring_web.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("otelRestTemplateBeanPostProcessor"));
  }

  @Test
  void envVarOverrideSpringStyle() {
    this.contextRunner
        // this is typically set via env var
        .withSystemProperties(
            "OTEL_TRACER_PROVIDER_PROCESSORS_0_BATCH_EXPORTER_OTLP_HTTP_ENDPOINT=http://custom:4318/v1/traces")
        .run(
            context ->
                assertThat(context)
                    .getBean(OpenTelemetry.class)
                    .isNotNull()
                    .satisfies(
                        c ->
                            assertThat(c.toString())
                                .contains(
                                    "OtlpHttpSpanExporter{endpoint=http://custom:4318/v1/traces")));
  }

  @Test
  void envVarOverrideOtelStyle() {
    this.contextRunner
        // this is typically set via env var
        .withSystemProperties("OTEL_EXPORTER_OTLP_ENDPOINT=http://custom:4318")
        .run(
            context ->
                assertThat(context)
                    .getBean(OpenTelemetry.class)
                    .isNotNull()
                    .satisfies(
                        c ->
                            assertThat(c.toString())
                                .contains(
                                    "OtlpHttpSpanExporter{endpoint=http://custom:4318/v1/traces")));
  }
}
