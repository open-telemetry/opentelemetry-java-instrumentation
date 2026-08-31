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
    assertThatThrownBy(() -> builder.addRules(classpathRules("jmx/rules/invalid.yaml")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void knownValidYaml() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    builder.addRules(classpathRules("jmx/rules/jvm.yaml"));
    builder.setMetrics(IncludeExclude.builder().setExcluded("excluded.metric").build());
    JmxTelemetry telemetry = builder.build();
    assertThat(telemetry).isNotNull();

    // by default include should contain all registered metrics, and the provided excluded should
    // be preserved as-is
    IncludeExclude includeExclude = telemetry.getMetrics();
    assertThat(includeExclude.getIncluded()).isNotEmpty();
    assertThat(includeExclude.getIncluded()).allMatch(m -> m.startsWith("jvm."));
    assertThat(includeExclude.getExcluded()).contains("excluded.metric");
  }

  @Test
  void metricsExplicitInclude() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop())
        .addRules(classpathRules("jmx/rules/jvm.yaml"))
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
  void includeStableAndUnstableBySystem(){
    // allows to provide a fallback to include embedded metrics per-system
    IncludeExclude includeInclude = IncludeExclude.builder().setIncluded("jvm").build();
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop())
        .addStableMetrics(includeInclude)
        .addUnstableMetrics(includeInclude);
    JmxTelemetry telemetry = builder.build();
    assertThat(telemetry).isNotNull();

    // all the jvm metrics should be included
    assertThat(telemetry.getMetrics().getIncluded()).allMatch(m -> m.startsWith("jvm."));
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
