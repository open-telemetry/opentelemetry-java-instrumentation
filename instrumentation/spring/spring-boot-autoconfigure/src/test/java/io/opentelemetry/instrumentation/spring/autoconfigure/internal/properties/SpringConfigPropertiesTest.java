/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.standard.SpelExpressionParser;

class SpringConfigPropertiesTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
          .withPropertyValues(
              "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none");

  public static Stream<Arguments> headerKeys() {
    return Arrays.stream(
            new String[] {
              "otel.exporter.otlp.traces.headers",
              "otel.exporter.otlp.metrics.headers",
              "otel.exporter.otlp.logs.headers",
              "otel.exporter.otlp.headers",
            })
        .map(Arguments::of);
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

  public static Stream<Arguments> listProperties() {
    return Stream.of(
        Arguments.of("otel.experimental.metrics.view.config", Arrays.asList("a", "b")),
        Arguments.of("otel.experimental.resource.disabled.keys", Arrays.asList("a", "b")),
        Arguments.of("otel.propagators", Arrays.asList("baggage", "b3")),
        Arguments.of("otel.logs.exporter", Collections.singletonList("console")),
        Arguments.of("otel.metrics.exporter", Collections.singletonList("console")),
        Arguments.of("otel.traces.exporter", Collections.singletonList("console")),
        Arguments.of(
            "otel.instrumentation.http.client.capture-request-headers", Arrays.asList("a", "b")),
        Arguments.of(
            "otel.instrumentation.http.client.capture-response-headers", Arrays.asList("a", "b")),
        Arguments.of(
            "otel.instrumentation.http.server.capture-request-headers", Arrays.asList("a", "b")),
        Arguments.of(
            "otel.instrumentation.http.server.capture-response-headers", Arrays.asList("a", "b")),
        Arguments.of("otel.instrumentation.http.known-methods", Arrays.asList("a", "b")));
  }

  @ParameterizedTest
  @MethodSource("listProperties")
  @DisplayName("should map list from application.yaml list")
  // See the application.yaml file
  void listsShouldWorkWithYaml(String key, List<String> expected) {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .run(
            context ->
                assertThat(getConfig(context).getList(key))
                    .containsExactlyInAnyOrderElementsOf(expected));
  }

  @Test
  @DisplayName("when map is set in properties in a row it should be available in config")
  void shouldInitializeAttributesByMap() {
    this.contextRunner
        .withPropertyValues(
            "otel.resource.attributes.environment=dev",
            "otel.resource.attributes.xyz=foo",
            "otel.resource.attributes.service.instance.id=id-example")
        .run(
            context -> {
              Map<String, String> fallback = new HashMap<>();
              fallback.put("fallback", "fallbackVal");
              fallback.put("otel.resource.attributes", "foo=fallback");

              SpringConfigProperties config = getConfig(context, fallback);

              assertThat(config.getMap("otel.resource.attributes"))
                  .contains(
                      entry("environment", "dev"),
                      entry("xyz", "foo"),
                      entry("service.instance.id", "id-example"),
                      entry("foo", "fallback"));

              assertThat(config.getString("fallback")).isEqualTo("fallbackVal");
            });
  }

  private static ConfigProperties getConfig(AssertableApplicationContext context) {
    return getConfig(context, Collections.emptyMap());
  }

  private static SpringConfigProperties getConfig(
      AssertableApplicationContext context, Map<String, String> fallback) {
    return new SpringConfigProperties(
        context.getBean("environment", Environment.class),
        new SpelExpressionParser(),
        context.getBean(OtlpExporterProperties.class),
        context.getBean(OtelResourceProperties.class),
        context.getBean(OtelSpringProperties.class),
        DefaultConfigProperties.createFromMap(fallback));
  }
}
