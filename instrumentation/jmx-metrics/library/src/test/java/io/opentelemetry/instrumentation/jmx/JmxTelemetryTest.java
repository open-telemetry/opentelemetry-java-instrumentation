/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.jmx.internal.InternalMetricsDefinitions;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JmxTelemetryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

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
    builder.addRules(classpathRules("jmx/rules/jvm-test_unstable.yaml"));
    JmxTelemetry telemetry = builder.build(testDefinitions());
    assertThat(telemetry).isNotNull();

    assertThat(builder.getRegisteredMetrics())
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.thread.count",
            "jvm.memory.used_after_last_gc",
            "jvm.file_descriptor.count",
            "jvm.file_descriptor.limit");

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
    JmxTelemetry telemetry = builder.build(testDefinitions());
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
    JmxTelemetry telemetry = builder.build(testDefinitions());
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
            .setInternalMetricsSystemFilter(
                IncludeExclude.builder().setIncluded("jvm-test").build())
            // include all unstable metrics (stable ones already included)
            .setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().build());

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
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            // enable stable metrics for every system
            .setInternalMetricsSystemFilter(IncludeExclude.builder().build());
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

    assertThat(telemetry.getMetrics()).isEqualTo(IncludeExclude.builder().build());
  }

  @Test
  void includeEveryMetric() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            // enable stable metrics for every system
            .setInternalMetricsSystemFilter(IncludeExclude.builder().build())
            // every system included by default, we just enable all unstable metrics
            .setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().build());

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
  void includeNothingByDefault() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    builder.build(testDefinitions());
    assertThat(builder.getRegisteredMetrics()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void customRuleSharingEmbeddedUnstableName(boolean includeUnstable) {
    String customRules =
        "rules:\n"
            + "  - bean: java.lang:type=Threading\n"
            + "    metricAttribute:\n"
            + "      test.source: const(custom)\n"
            + "    mapping:\n"
            + "      ThreadCount:\n"
            + "        metric: test.collision.count\n"
            + "        type: gauge\n"
            + "        unit: '{thread}'\n"
            + "        desc: Current number of threads.\n";
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(testing.getOpenTelemetry())
            .addRules(new ByteArrayInputStream(customRules.getBytes(UTF_8)))
            .setInternalMetricsSystemFilter(
                IncludeExclude.builder().setIncluded("test-collision").build());
    if (includeUnstable) {
      builder.setInternalMetricsUnstableMetricsFilter(
          IncludeExclude.builder().setIncluded("test.collision.count").build());
    }

    cleanup.deferCleanup(builder.build(collisionDefinitions()).start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("test.collision.count"));
    Collection<String> sources =
        testing.metrics().stream()
            .filter(metric -> metric.getName().equals("test.collision.count"))
            .flatMap(metric -> metric.getLongGaugeData().getPoints().stream())
            .map(point -> point.getAttributes().get(stringKey("test.source")))
            .collect(toSet());
    assertThat(sources).contains("custom");
    if (includeUnstable) {
      assertThat(sources).contains("embedded");
    } else {
      assertThat(sources).doesNotContain("embedded");
    }
  }

  private static InternalMetricsDefinitions collisionDefinitions() {
    return new InternalMetricsDefinitions(JmxTelemetryTest.class.getClassLoader()) {
      @Override
      public Set<String> getSupportedSystems() {
        return singleton("test-collision");
      }
    };
  }

  private static InternalMetricsDefinitions testDefinitions() {
    // provides a test-only implementation that allows to avoid loading existing metrics definitions
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
