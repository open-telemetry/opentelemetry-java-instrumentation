/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
  @DisplayName("should map headers from spring properties with user supplied OpenTelemetry bean")
  void mapFlatHeadersWithUserSuppliedOtelBean(String key) {
    this.contextRunner
        .withSystemProperties(key + "=a=1,b=2")
        .withBean(OpenTelemetry.class, OpenTelemetry::noop)
        .run(
            context -> {
              // don't crash if OpenTelemetry bean is provided
            });
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

  public static Stream<Arguments> propertyCachingTestCases() {
    return Stream.of(
        // property, typeClass, assertion
        Arguments.of(
            "otel.service.name=test-service",
            String.class,
            (Consumer<SpringConfigProperties>)
                config ->
                    assertThat(config.getString("otel.service.name")).isEqualTo("test-service")),
        Arguments.of(
            "otel.exporter.otlp.enabled=true",
            Boolean.class,
            (Consumer<SpringConfigProperties>)
                config -> assertThat(config.getBoolean("otel.exporter.otlp.enabled")).isTrue()),
        Arguments.of(
            "otel.metric.export.interval=10",
            Integer.class,
            (Consumer<SpringConfigProperties>)
                config -> assertThat(config.getInt("otel.metric.export.interval")).isEqualTo(10)),
        Arguments.of(
            "otel.bsp.schedule.delay=5000",
            Long.class,
            (Consumer<SpringConfigProperties>)
                config -> assertThat(config.getLong("otel.bsp.schedule.delay")).isEqualTo(5000L)),
        Arguments.of(
            "otel.traces.sampler.arg=0.5",
            Double.class,
            (Consumer<SpringConfigProperties>)
                config -> assertThat(config.getDouble("otel.traces.sampler.arg")).isEqualTo(0.5)),
        Arguments.of(
            "otel.bsp.export.timeout=30s",
            String.class,
            (Consumer<SpringConfigProperties>)
                config ->
                    assertThat(config.getDuration("otel.bsp.export.timeout"))
                        .isEqualByComparingTo(java.time.Duration.ofSeconds(30))),
        Arguments.of(
            "otel.attribute.value.length.limit=256",
            List.class,
            (Consumer<SpringConfigProperties>)
                config ->
                    assertThat(config.getList("otel.attribute.value.length.limit"))
                        .containsExactly("256")));
  }

  @ParameterizedTest
  @MethodSource("propertyCachingTestCases")
  @DisplayName("should cache property lookups and call Environment.getProperty() only once")
  void propertyCaching(
      String property, Class<?> typeClass, Consumer<SpringConfigProperties> assertion) {
    String propertyName = property.split("=")[0];

    this.contextRunner
        .withPropertyValues(property)
        .run(
            context -> {
              Environment realEnvironment = context.getBean("environment", Environment.class);
              Environment spyEnvironment = spy(realEnvironment);

              SpringConfigProperties config =
                  new SpringConfigProperties(
                      spyEnvironment,
                      new SpelExpressionParser(),
                      context.getBean(OtlpExporterProperties.class),
                      context.getBean(OtelResourceProperties.class),
                      context.getBean(OtelSpringProperties.class),
                      DefaultConfigProperties.createFromMap(emptyMap()));

              for (int i = 0; i < 100; i++) {
                assertion.accept(config);
              }

              verify(spyEnvironment, times(1)).getProperty(eq(propertyName), eq(typeClass));
            });
  }

  @Test
  @DisplayName("should cache map property lookups and call Environment.getProperty() only once")
  void mapPropertyCaching() {
    this.contextRunner
        .withSystemProperties(
            "otel.instrumentation.common.peer-service-mapping={'host1':'serviceA','host2':'serviceB'}")
        .run(
            context -> {
              Environment realEnvironment = context.getBean("environment", Environment.class);
              Environment spyEnvironment = spy(realEnvironment);

              SpringConfigProperties config =
                  new SpringConfigProperties(
                      spyEnvironment,
                      new SpelExpressionParser(),
                      context.getBean(OtlpExporterProperties.class),
                      context.getBean(OtelResourceProperties.class),
                      context.getBean(OtelSpringProperties.class),
                      DefaultConfigProperties.createFromMap(emptyMap()));

              for (int i = 0; i < 100; i++) {
                Map<String, String> mapping =
                    config.getMap("otel.instrumentation.common.peer-service-mapping");
                assertThat(mapping)
                    .containsEntry("host1", "serviceA")
                    .containsEntry("host2", "serviceB");
              }

              // Map properties call getProperty(name) which delegates to getProperty(name,
              // String.class)
              verify(spyEnvironment, times(1))
                  .getProperty(
                      eq("otel.instrumentation.common.peer-service-mapping"), eq(String.class));
            });
  }

  private static ConfigProperties getConfig(AssertableApplicationContext context) {
    return getConfig(context, emptyMap());
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
