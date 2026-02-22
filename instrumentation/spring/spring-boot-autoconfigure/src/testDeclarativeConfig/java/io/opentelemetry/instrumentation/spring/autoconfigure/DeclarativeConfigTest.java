/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.web.SpringWebInstrumentationAutoConfiguration;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
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
        context ->
            assertThat(context)
                .getBean("openTelemetry", OpenTelemetry.class)
                .isNotNull()
                .satisfies(
                    o -> {
                      DeclarativeConfigProperties config =
                          DeclarativeConfigUtil.getInstrumentationConfig(o, "foo");
                      assertThat(config.getString("string_key")).isEqualTo("string_value");
                      assertThat(config.getBoolean("bool_key")).isTrue();
                      assertThat(config.getDouble("double_key")).isEqualTo(3.14);
                      assertThat(config.getLong("int_key")).isEqualTo(42);
                    }));
  }

  @Test
  @SetEnvironmentVariable(
      key = "OTEL_INSTRUMENTATION/DEVELOPMENT_JAVA_FOO_STRING_KEY",
      value = "new_value")
  @SetEnvironmentVariable(
      key = "OTEL_INSTRUMENTATION/DEVELOPMENT_JAVA_FOO_BOOL_KEY",
      value = "false")
  @SetEnvironmentVariable(key = "OTEL_INSTRUMENTATION/DEVELOPMENT_JAVA_FOO_INT_KEY", value = "43")
  @SetEnvironmentVariable(
      key = "OTEL_INSTRUMENTATION/DEVELOPMENT_JAVA_FOO_DOUBLE_KEY",
      value = "4.14")
  @SetEnvironmentVariable(
      key = "OTEL_TRACER_PROVIDER_PROCESSORS_0_BATCH_EXPORTER_OTLP_HTTP_ENDPOINT",
      value = "http://custom:4318/v1/traces")
  void envVarOverrideSpringStyle() {
    this.contextRunner.run(
        context ->
            assertThat(context)
                .getBean(OpenTelemetry.class)
                .isNotNull()
                .satisfies(
                    o -> {
                      DeclarativeConfigProperties config =
                          DeclarativeConfigUtil.getInstrumentationConfig(o, "foo");
                      assertThat(config.getString("string_key")).isEqualTo("new_value");
                      assertThat(config.getBoolean("bool_key")).isFalse();
                      assertThat(config.getDouble("double_key")).isEqualTo(4.14);
                      assertThat(config.getLong("int_key")).isEqualTo(43);

                      assertThat(o.toString())
                          .contains("OtlpHttpSpanExporter{endpoint=http://custom:4318/v1/traces");
                    }));
  }

  @Test
  @SetEnvironmentVariable(key = "STRING_ENV", value = "string_value")
  @SetEnvironmentVariable(key = "BOOL_ENV", value = "true")
  @SetEnvironmentVariable(key = "INT_ENV", value = "42")
  @SetEnvironmentVariable(key = "DOUBLE_ENV", value = "3.14")
  @SetEnvironmentVariable(key = "OTEL_EXPORTER_OTLP_ENDPOINT", value = "http://custom:4318")
  void envVarOverrideOtelStyle() {
    this.contextRunner.run(
        context ->
            assertThat(context)
                .getBean(OpenTelemetry.class)
                .isNotNull()
                .satisfies(
                    o -> {
                      DeclarativeConfigProperties config =
                          DeclarativeConfigUtil.getInstrumentationConfig(o, "foo");
                      assertThat(config.getString("string_key")).isEqualTo("string_value");
                      assertThat(config.getString("string_key_with_env")).isEqualTo("string_value");
                      assertThat(config.getString("string_key_with_env_quoted"))
                          .isEqualTo("string_value");

                      assertThat(config.getBoolean("bool_key")).isTrue();
                      assertThat(config.getBoolean("bool_key_with_env")).isTrue();
                      assertThat(config.getBoolean("bool_key_with_env_quoted"))
                          .isTrue(); // quoted "true" works because of coercion
                      assertThat(config.getString("bool_key_with_env_quoted")).isEqualTo("true");

                      assertThat(config.getDouble("double_key")).isEqualTo(3.14);
                      assertThat(config.getDouble("double_key_with_env")).isEqualTo(3.14);
                      assertThat(config.getDouble("double_key_with_env_quoted"))
                          .isEqualTo(3.14); // quoted "3.14" works because of coercion
                      assertThat(config.getString("double_key_with_env_quoted")).isEqualTo("3.14");

                      assertThat(config.getLong("int_key")).isEqualTo(42);
                      assertThat(config.getLong("int_key_with_env")).isEqualTo(42);
                      assertThat(config.getLong("int_key_with_env_quoted"))
                          .isEqualTo(42); // quoted "42" works because of coercion
                      assertThat(config.getString("int_key_with_env_quoted")).isEqualTo("42");
                    }));
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
}
