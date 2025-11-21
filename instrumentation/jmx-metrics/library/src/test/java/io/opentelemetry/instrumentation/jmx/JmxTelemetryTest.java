/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JmxTelemetryTest {

  @Test
  void createDefault() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThat(builder.build()).isNotNull();
  }

  @Test
  void missingClasspathTarget() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.addRules(null, "something is missing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found")
        .hasMessageContaining("something is missing");
  }

  @Test
  void invalidClasspathTarget() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> addClasspathRules(builder, "jmx/rules/invalid.yaml"))
        .isInstanceOf(IllegalArgumentException.class)
        .describedAs("must have an exception message including the invalid resource path")
        .hasMessageContaining("jmx/rules/invalid.yaml");
  }

  @Test
  void knownValidYaml() {
    JmxTelemetryBuilder jmxtelemetry = JmxTelemetry.builder(OpenTelemetry.noop());
    addClasspathRules(jmxtelemetry, "jmx/rules/jvm.yaml");
    assertThat(jmxtelemetry.build()).isNotNull();
  }

  private static void addClasspathRules(JmxTelemetryBuilder builder, String path) {
    InputStream input = JmxTelemetryTest.class.getClassLoader().getResourceAsStream(path);
    builder.addRules(input, path);
  }

  @Test
  void tryInvalidYaml(@TempDir Path dir) throws Exception {
    Path invalid = Files.createTempFile(dir, "invalid", ".yaml");
    Files.write(invalid, ":this !is /not YAML".getBytes(StandardCharsets.UTF_8));
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    try (InputStream input = Files.newInputStream(invalid)) {
      assertThatThrownBy(() -> builder.addRules(input, invalid.toString()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void invalidStartDelay() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.beanDiscoveryDelay(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
