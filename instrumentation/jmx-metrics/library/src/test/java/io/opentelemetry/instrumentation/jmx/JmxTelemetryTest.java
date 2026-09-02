/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.jmx.internal.InternalMetricsDefinitions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    builder.setMetrics(IncludeExclude.builder().setExcluded("jvm.thread.count").build());
    JmxTelemetry telemetry = builder.build();
    assertThat(telemetry).isNotNull();

    // by default include should contain all registered metrics, and the provided excluded should
    // be preserved as-is
    IncludeExclude includeExclude = telemetry.getMetrics();
    assertThat(includeExclude.getIncluded())
        .containsExactlyInAnyOrder("jvm.memory.committed", "jvm.memory.used", "jvm.thread.count");
    assertThat(includeExclude.getExcluded()).containsExactlyInAnyOrder("jvm.thread.count");
    // when both included and excluded are provided, the excluded should take precedence
    assertThat(includeExclude.matches("jvm.thread.count")).isFalse();
  }

  @Test
  void metricsExplicitInclude() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .addRules(classpathRules("jmx/rules/jvm-test.yaml"))
            .setMetrics(
                IncludeExclude.builder()
                    .setIncluded("jvm.memory.used")
                    .setExcluded("missing.metric")
                    .build());
    JmxTelemetry telemetry = builder.build();
    assertThat(telemetry).isNotNull();

    assertThat(telemetry.getMetrics().getIncluded()).containsOnly("jvm.memory.used");
    assertThat(telemetry.getMetrics().getExcluded()).containsOnly("missing.metric");
  }

  @Test
  void includeBothStableAndUnstableBySystem() {
    // allows to provide a fallback to include embedded metrics per-system
    IncludeExclude includeInclude = IncludeExclude.builder().setIncluded("jvm-test").build();
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .loadStableMetrics(includeInclude)
            .loadUnstableMetrics(includeInclude);

    stableUnstableTest(builder, "jmx/rules/jvm-test.yaml", "jmx/rules/jvm-test_unstable.yaml");
  }

  @Test
  void includeAllStableMetrics() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .loadStableMetrics(IncludeExclude.builder().build());

    JmxTelemetry telemetry = stableUnstableTest(builder, "jmx/rules/jvm-test.yaml");
    assertThat(telemetry.getMetrics().getIncluded())
        .containsExactlyInAnyOrder("jvm.memory.committed", "jvm.memory.used", "jvm.thread.count");
  }

  @Test
  void includeAllUnstableMetrics() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .loadUnstableMetrics(IncludeExclude.builder().build());

    JmxTelemetry telemetry = stableUnstableTest(builder, "jmx/rules/jvm-test_unstable.yaml");
    assertThat(telemetry.getMetrics().getIncluded())
        .containsExactlyInAnyOrder(
            "jvm.thread.file_descriptor.limit", "jvm.thread.file_descriptor.count");
  }

  @Test
  void includeEveryMetric() {
    JmxTelemetryBuilder builder =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .loadStableMetrics(IncludeExclude.builder().build())
            .loadUnstableMetrics(IncludeExclude.builder().build());

    JmxTelemetry telemetry =
        stableUnstableTest(builder, "jmx/rules/jvm-test.yaml", "jmx/rules/jvm-test_unstable.yaml");
    assertThat(telemetry.getMetrics().getIncluded())
        .containsExactlyInAnyOrder(
            "jvm.thread.file_descriptor.limit",
            "jvm.thread.file_descriptor.count",
            "jvm.memory.committed",
            "jvm.memory.used",
            "jvm.thread.count");
  }

  @Test
  void includeNothingByDefault() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    JmxTelemetry telemetry = stableUnstableTest(builder);
    assertThat(telemetry.getMetrics().getIncluded()).isEmpty();
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
