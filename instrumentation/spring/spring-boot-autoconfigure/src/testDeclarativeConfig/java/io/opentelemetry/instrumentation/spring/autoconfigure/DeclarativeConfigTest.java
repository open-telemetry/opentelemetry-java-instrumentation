/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
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
                .hasBean("openTelemetry")
                .hasBean("otelProperties")
                .getBean(InstrumentationConfig.class)
                .isNotNull()
                .satisfies(
                    c ->
                        assertThat(c.getDeclarativeConfig("foo"))
                            .isNotNull()
                            .satisfies(
                                instrumentationConfig ->
                                    assertThat(instrumentationConfig.getString("bar"))
                                        .isEqualTo("baz"))));
  }

  @Test
  void shouldInitializeSdkWhenNotDisabled() {
    this.contextRunner
        .withPropertyValues("otel.disabled=false")
        .run(
            context ->
                assertThat(context).getBean("openTelemetry").isInstanceOf(OpenTelemetrySdk.class));
  }

  @Test
  void shouldInitializeNoopOpenTelemetryWhenSdkIsDisabled() {
    this.contextRunner
        .withPropertyValues(
            "otel.disabled=true",
            "otel.resource.attributes=service.name=workflow-backend-dev,service.version=3c8f9ce9")
        .run(
            context ->
                assertThat(context).getBean("openTelemetry").isEqualTo(OpenTelemetry.noop()));
  }
}
