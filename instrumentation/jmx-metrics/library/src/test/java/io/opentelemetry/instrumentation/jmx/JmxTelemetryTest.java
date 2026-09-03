/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.stream.Stream;
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
    JmxTelemetry telemetry = builder.build();
    assertThat(telemetry).isNotNull();

    assertThat(builder.getRegisteredMetrics())
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.thread.count",
            "jvm.memory.used_after_last_gc");

    checkMetricsIncluded(telemetry.getMetrics(), builder.getRegisteredMetrics());
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
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.thread.count",
            "jvm.memory.used_after_last_gc");

    checkMetricsIncluded(
        telemetry.getMetrics(),
        "jvm.memory.committed",
        "jvm.memory.used",
        "jvm.memory.limit",
        "jvm.memory.used_after_last_gc");
    checkMetricsExcluded(telemetry.getMetrics(), "jvm.thread.count");
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

    checkMetricsIncluded(telemetry.getMetrics(), "jvm.memory.used");
    checkMetricsExcluded(telemetry.getMetrics(), "jvm.memory.limit", "jvm.thread.count");
  }

  @Test
  void includeBothStableAndUnstableBySystem() {
    // allows to provide a fallback to include embedded metrics per-system
    IncludeExclude includeInclude = IncludeExclude.builder().setIncluded("jvm-test").build();
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .loadStableMetrics(includeInclude)
            .loadUnstableMetrics(includeInclude);

    JmxTelemetry telemetry =
        stableUnstableTest(builder, "jmx/rules/jvm-test.yaml", "jmx/rules/jvm-test_unstable.yaml");

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
    checkMetricsIncluded(telemetry.getMetrics(), builder.getRegisteredMetrics());
  }

  @Test
  void includeAllStableMetrics() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .loadStableMetrics(IncludeExclude.builder().build());

    JmxTelemetry telemetry = stableUnstableTest(builder, "jmx/rules/jvm-test.yaml");

    assertThat(builder.getRegisteredMetrics())
        .containsExactlyInAnyOrder(
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.memory.limit",
            "jvm.thread.count",
            "jvm.memory.used_after_last_gc");

    // no filtering is applied here, so we should get all metrics
    checkMetricsIncluded(telemetry.getMetrics(), builder.getRegisteredMetrics());
  }

  @Test
  void includeAllUnstableMetrics() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .loadUnstableMetrics(IncludeExclude.builder().build());

    JmxTelemetry telemetry = stableUnstableTest(builder, "jmx/rules/jvm-test_unstable.yaml");

    assertThat(builder.getRegisteredMetrics())
        .containsExactlyInAnyOrder("jvm.file_descriptor.limit", "jvm.file_descriptor.count");

    // no filtering is applied here, so we should get all metrics
    checkMetricsIncluded(telemetry.getMetrics(), builder.getRegisteredMetrics());
  }

  @Test
  void includeEveryMetric() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .loadStableMetrics(IncludeExclude.builder().build())
            .loadUnstableMetrics(IncludeExclude.builder().build());

    JmxTelemetry telemetry =
        stableUnstableTest(builder, "jmx/rules/jvm-test.yaml", "jmx/rules/jvm-test_unstable.yaml");

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
    checkMetricsIncluded(telemetry.getMetrics(), builder.getRegisteredMetrics());
  }

  @Test
  void includeNothingByDefault() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    stableUnstableTest(builder);

    assertThat(builder.getRegisteredMetrics()).isEmpty();
  }

  private static void checkMetricsIncluded(IncludeExclude metrics, String... metricNames) {
    checkMetricsIncluded(metrics, asList(metricNames));
  }

  private static void checkMetricsIncluded(IncludeExclude metrics, Collection<String> metricNames) {
    Set<String> included = metricNames.stream().filter(metrics::matches).collect(toSet());
    assertThat(included).containsExactlyInAnyOrderElementsOf(metricNames);
  }

  private static void checkMetricsExcluded(IncludeExclude metrics, String... metricNames) {
    Stream.of(metricNames)
        .forEach(metricName -> assertThat(metrics.matches(metricName)).isEqualTo(false));
  }

  private static JmxTelemetry stableUnstableTest(
      JmxTelemetryBuilder builder, String... expectedRules) {
    TestMetricsDefinitions metricsDefinitions = new TestMetricsDefinitions();
    assertThat(builder.getInternalRulesToLoad(metricsDefinitions))
        .containsExactlyInAnyOrder(expectedRules);

    JmxTelemetry telemetry = builder.build(metricsDefinitions);
    assertThat(telemetry).isNotNull();

    if (expectedRules.length > 0) {
      // all the jvm metrics should be included
      assertThat(telemetry.getMetrics().getIncluded()).allMatch(m -> m.startsWith("jvm."));
    }
    return telemetry;
  }

  private static class TestMetricsDefinitions extends InternalMetricsDefinitions {

    private TestMetricsDefinitions() {
      super(JmxTelemetryTest.class.getClassLoader());
    }

    @Override
    public Set<String> getSupportedSystems() {
      return singleton("jvm-test");
    }
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
