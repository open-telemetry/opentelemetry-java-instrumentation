/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LoggingExporterAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  LoggingExporterAutoConfiguration.class, OpenTelemetryAutoConfiguration.class));

  private static Stream<Arguments> exporterAddedCases() {
    return Stream.of(
        arguments(
            "debug enabled adds the exporter",
            new String[] {"otel.spring-starter.debug=true", "otel.traces.exporter=none"}),
        arguments(
            "exporter already configured stays present exactly once",
            new String[] {"otel.spring-starter.debug=true", "otel.traces.exporter=logging"}));
  }

  private static Stream<Arguments> exporterAbsentCases() {
    return Stream.of(
        arguments("debug unset leaves the exporter off", new String[0]),
        arguments(
            "debug disabled leaves the exporter off",
            new String[] {"otel.spring-starter.debug=false", "otel.traces.exporter=none"}));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("exporterAddedCases")
  void loggingExporterPresent(String name, String[] propertyValues) {
    runner
        .withPropertyValues(propertyValues)
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .containsOnlyOnce("LoggingSpanExporter"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("exporterAbsentCases")
  void loggingExporterAbsent(String name, String[] propertyValues) {
    runner
        .withPropertyValues(propertyValues)
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .doesNotContain("LoggingSpanExporter"));
  }
}
