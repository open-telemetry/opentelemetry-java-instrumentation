/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.jmx.internal.InternalMetricsDefinitions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JmxTelemetryTest {

  @Test
  void createDefault() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThat(builder.build()).isNotNull();
  }

  @Test
  void throwsExceptionOnNullInput() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.addRules((InputStream) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> builder.addRules((Path) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidClasspathTarget() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.addRules(classpathRules("jmx/rules/invalid.yaml")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void knownValidYaml() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    builder.addRules(classpathRules("jmx/rules/jvm-test.yaml"));
    builder.internalMetricsSystemFilter(IncludeExclude.builder().setIncluded("jvm-test").build());
    JmxTelemetry telemetry = builder.build();
    assertThat(telemetry).isNotNull();

    assertThat(builder.getRegisteredMetrics())
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.thread.count",
            "jvm.memory.used_after_last_gc");

    assertThat(getFilteredMetrics(telemetry.getMetrics(), builder.getRegisteredMetrics()))
        .containsExactlyInAnyOrderElementsOf(builder.getRegisteredMetrics());
  }

  private static Collection<String> getFilteredMetrics(
      IncludeExclude filter, Collection<String> registeredMetrics) {
    return registeredMetrics.stream().filter(filter::matches).collect(toSet());
  }

  @Test
  void metricsExclude() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .addRules(classpathRules("jmx/rules/jvm-test.yaml"));
    builder.setMetrics(IncludeExclude.builder().setExcluded("jvm.thread.count").build());
    JmxTelemetry telemetry = builder.build();
    assertThat(telemetry).isNotNull();

    assertThat(builder.getRegisteredMetrics())
        .contains(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.thread.count",
            "jvm.memory.used_after_last_gc");

    assertThat(getFilteredMetrics(telemetry.getMetrics(), builder.getRegisteredMetrics()))
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.memory.used_after_last_gc")
        .doesNotContain("jvm.thread.count");
  }

  @Test
  void metricsExplicitInclude() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .addRules(classpathRules("jmx/rules/jvm-test.yaml"));
    builder.setMetrics(
        IncludeExclude.builder()
            .setIncluded("jvm.memory.used")
            .setExcluded("jvm.thread.count")
            .build());
    JmxTelemetry telemetry = builder.build();
    assertThat(telemetry).isNotNull();

    assertThat(builder.getRegisteredMetrics())
        .contains("jvm.memory.used", "jvm.memory.limit", "jvm.thread.count");

    assertThat(getFilteredMetrics(telemetry.getMetrics(), builder.getRegisteredMetrics()))
        .containsExactlyInAnyOrder("jvm.memory.used")
        .doesNotContain("jvm.memory.limit", "jvm.thread.count");
  }

  @Test
  void legacyIncludeBySystem() {
    // allows to provide a fallback to include embedded metrics per-system
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            // only load explicitly listed systems
            .internalMetricsSystemFilter(IncludeExclude.builder().setIncluded("jvm-test").build())
            // include all unstable metrics (stable ones already included)
            .internalMetricsUnstableMetricsFilter(IncludeExclude.builder().build());

    JmxTelemetry telemetry = builder.build(testDefinitions());

    assertThat(builder.getRegisteredMetrics())
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.file_descriptor.limit",
            "jvm.file_descriptor.count",
            "jvm.thread.count",
            "jvm.memory.used_after_last_gc");

    // no filtering is applied here, so we should get all metrics
    assertThat(getFilteredMetrics(telemetry.getMetrics(), builder.getRegisteredMetrics()))
        .containsExactlyInAnyOrderElementsOf(builder.getRegisteredMetrics());
  }

  @Test
  void includeAllStableMetrics() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    JmxTelemetry telemetry = builder.build(testDefinitions());

    assertThat(builder.getRegisteredMetrics())
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.thread.count",
            "jvm.memory.used_after_last_gc",
            // registered, but should be filtered
            "jvm.file_descriptor.count",
            "jvm.file_descriptor.limit");

    // non-stable metrics should be filtered
    assertThat(getFilteredMetrics(telemetry.getMetrics(), builder.getRegisteredMetrics()))
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.thread.count",
            "jvm.memory.used_after_last_gc")
        .doesNotContain("jvm.file_descriptor.count", "jvm.file_descriptor.limit");
  }

  @Test
  void includeEveryMetric() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            // every system included by default, we just enable all unstable metrics
            .internalMetricsUnstableMetricsFilter(IncludeExclude.builder().build());

    JmxTelemetry telemetry = builder.build(testDefinitions());

    assertThat(builder.getRegisteredMetrics())
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.memory.used_after_last_gc",
            "jvm.file_descriptor.limit",
            "jvm.file_descriptor.count",
            "jvm.thread.count");

    // no filtering is applied here, so we should get all metrics
    assertThat(getFilteredMetrics(telemetry.getMetrics(), builder.getRegisteredMetrics()))
        .containsExactlyInAnyOrderElementsOf(builder.getRegisteredMetrics());
  }

  @Test
  void includeNothing() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .internalMetricsSystemFilter(IncludeExclude.builder().setExcluded("*").build());
    builder.build();
    assertThat(builder.getRegisteredMetrics()).isEmpty();
  }

  private static InternalMetricsDefinitions testDefinitions() {
    return new InternalMetricsDefinitions(JmxTelemetryTest.class.getClassLoader()) {
      @Override
      public Set<String> getSupportedSystems() {
        return singleton("jvm-test");
      }
    };
  }

  private static InputStream classpathRules(String path) {
    return JmxTelemetryTest.class.getClassLoader().getResourceAsStream(path);
  }

  @Test
  void invalidExternalYaml(@TempDir Path dir) throws IOException {
    Path invalid = Files.createTempFile(dir, "invalid", ".yaml");
    Files.write(invalid, ":this !is /not YAML".getBytes(UTF_8));
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.addRules(invalid))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidStartDelay() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.beanDiscoveryDelay(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
