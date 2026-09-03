/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    assertThatThrownBy(() -> addClasspathRules(builder, "jmx/rules/invalid.yaml"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void knownValidYaml() {
    JmxTelemetryBuilder jmxtelemetry = JmxTelemetry.builder(OpenTelemetry.noop());
    addClasspathRules(jmxtelemetry, "jmx/rules/jvm.yaml");
    JmxTelemetry telemetry = jmxtelemetry.build();
    assertThat(telemetry).isNotNull();

    IncludeExclude includeExclude = telemetry.getMetrics();
    checkMetricIncluded(includeExclude, "jvm.memory.used", true);
    checkMetricIncluded(includeExclude, "jvm.memory.limit", true);
    checkMetricIncluded(includeExclude, "jvm.thread.count", true);
  }

  @Test
  void metricsExclude() {
    JmxTelemetryBuilder jmxtelemetry = JmxTelemetry.builder(OpenTelemetry.noop());
    addClasspathRules(jmxtelemetry, "jmx/rules/jvm.yaml");
    jmxtelemetry.setMetrics(IncludeExclude.builder().setExcluded("jvm.thread.count").build());
    JmxTelemetry telemetry = jmxtelemetry.build();
    assertThat(telemetry).isNotNull();

    IncludeExclude includeExclude = telemetry.getMetrics();
    checkMetricIncluded(includeExclude, "jvm.memory.used", true);
    checkMetricIncluded(includeExclude, "jvm.memory.limit", true);
    checkMetricIncluded(includeExclude, "jvm.thread.count", false);
  }

  @Test
  void metricsExplicitInclude() {
    JmxTelemetryBuilder jmxtelemetry = JmxTelemetry.builder(OpenTelemetry.noop());
    addClasspathRules(jmxtelemetry, "jmx/rules/jvm.yaml");
    jmxtelemetry.setMetrics(
        IncludeExclude.builder()
            .setIncluded("jvm.memory.used")
            .setExcluded("jvm.thread.count")
            .build());
    JmxTelemetry telemetry = jmxtelemetry.build();
    assertThat(telemetry).isNotNull();

    IncludeExclude includeExclude = telemetry.getMetrics();
    checkMetricIncluded(includeExclude, "jvm.memory.used", true);
    checkMetricIncluded(includeExclude, "jvm.memory.limit", false);
    checkMetricIncluded(includeExclude, "jvm.thread.count", false);
  }

  private static void checkMetricIncluded(IncludeExclude metrics, String metric, boolean expected) {
    assertThat(metrics.matches(metric)).isEqualTo(expected);
  }

  private static void addClasspathRules(JmxTelemetryBuilder builder, String path) {
    InputStream input = JmxTelemetryTest.class.getClassLoader().getResourceAsStream(path);
    builder.addRules(input);
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
