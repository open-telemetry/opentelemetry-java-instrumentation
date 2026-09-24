/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

class DeclarativeSpanLoggingTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class));

  // Spring resolves configuration as Strings. The bootstrap customizer must use typed-getter
  // conversion without depending on the ConfigProvider bean backed by the completed SDK.
  @Test
  void debugEnabled() {
    runner
        .withPropertyValues(
            "otel.file_format=1.1",
            "otel.instrumentation/development.java.spring_starter.debug=true")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .containsOnlyOnce("LoggingSpanExporter"));
  }

  @Test
  void debugDisabled() {
    runner
        .withPropertyValues(
            "otel.file_format=1.1",
            "otel.instrumentation/development.java.spring_starter.debug=false")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .doesNotContain("LoggingSpanExporter"));
  }

  @Test
  void debugUnset() {
    runner
        .withPropertyValues("otel.file_format=1.1")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .doesNotContain("LoggingSpanExporter"));
  }

  @Test
  void systemPropertyOverridesDebug() {
    runner
        .withPropertyValues("otel.file_format=1.1")
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addLast(
                        new MapPropertySource(
                            "applicationConfig",
                            singletonMap(
                                "otel.instrumentation/development.java.spring_starter.debug",
                                "true"))))
        .withSystemProperties("otel.instrumentation/development.java.spring_starter.debug=false")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .doesNotContain("LoggingSpanExporter"));
  }

  @Test
  void loggingExporterMissing() {
    runner
        .withClassLoader(new FilteredClassLoader(LoggingSpanExporter.class))
        .withPropertyValues(
            "otel.file_format=1.1",
            "otel.instrumentation/development.java.spring_starter.debug=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().doesNotHaveBean("spanLoggingCustomizerProvider");
              assertThat(context.getBean(OpenTelemetry.class).toString())
                  .doesNotContain("LoggingSpanExporter");
            });
  }
}
