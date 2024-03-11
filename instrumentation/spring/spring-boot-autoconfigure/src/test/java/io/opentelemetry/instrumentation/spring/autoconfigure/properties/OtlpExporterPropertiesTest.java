/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.standard.SpelExpressionParser;

class OtlpExporterPropertiesTest {

  private static final String[] HEADER_KEYS = {
    "otel.exporter.otlp.traces.headers",
    "otel.exporter.otlp.metrics.headers",
    "otel.exporter.otlp.logs.headers",
    "otel.exporter.otlp.headers",
  };

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
          .withPropertyValues(
              "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none");

  public static Stream<Arguments> headerKeys() {
    return Arrays.stream(HEADER_KEYS).map(Arguments::of);
  }

  @Test
  @DisplayName("test all property types")
  void allTypes() {
    this.contextRunner
        .withPropertyValues(
            "otel.exporter.otlp.enabled=true",
            "otel.exporter.otlp.timeout=1s",
            "otel.exporter.otlp.compression=gzip")
        .run(
            context -> {
              ConfigProperties config = getConfig(context);
              assertThat(config.getString("otel.exporter.otlp.compression")).isEqualTo("gzip");
              assertThat(config.getBoolean("otel.exporter.otlp.enabled")).isTrue();
              assertThat(config.getDuration("otel.exporter.otlp.timeout"))
                  .isEqualByComparingTo(java.time.Duration.ofSeconds(1));
            });
  }

  @ParameterizedTest
  @MethodSource("headerKeys")
  @DisplayName("should map headers from spring properties")
  void mapFlatHeaders(String key) {
    this.contextRunner
        .withSystemProperties(key + "=a=1,b=2")
        .run(
            context ->
                assertThat(getConfig(context).getMap(key))
                    .containsExactly(entry("a", "1"), entry("b", "2")));
  }

  @ParameterizedTest
  @MethodSource("headerKeys")
  @DisplayName("should map headers from spring application.yaml")
  void mapObjectHeaders(String key) {
    this.contextRunner
        .withPropertyValues(key + ".a=1", key + ".b=2")
        .run(
            context ->
                assertThat(getConfig(context).getMap(key))
                    .containsExactly(entry("a", "1"), entry("b", "2")));
  }

  private static ConfigProperties getConfig(AssertableApplicationContext context) {
    return new SpringConfigProperties(
        context.getBean("environment", Environment.class),
        new SpelExpressionParser(),
        context.getBean(OtlpExporterProperties.class),
        new OtelResourceProperties(),
        new PropagationProperties(),
        DefaultConfigProperties.createFromMap(Collections.emptyMap()));
  }
}
