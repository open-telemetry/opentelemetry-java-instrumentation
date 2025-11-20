/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
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
    assertThatThrownBy(() -> builder.addClassPathResourceRules("should-not-exist"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found")
        .hasMessageContaining("should-not-exist");
  }

  @Test
  void invalidClasspathTarget() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.addClassPathResourceRules("jmx/rules/invalid.yaml"))
        .isInstanceOf(IllegalArgumentException.class)
        .describedAs("must have an exception message including the invalid resource path")
        .hasMessageContaining("jmx/rules/invalid.yaml");
  }

  @Test
  void knownClassPathTarget() {
    JmxTelemetry jmxtelemetry =
        JmxTelemetry.builder(OpenTelemetry.noop())
            .addClassPathResourceRules("jmx/rules/jvm.yaml")
            .build();
    assertThat(jmxtelemetry).isNotNull();
  }

  @Test
  void invalidExternalYaml(@TempDir Path dir) throws Exception {
    Path invalid = Files.createTempFile(dir, "invalid", ".yaml");
    Files.write(invalid, ":this !is /not YAML".getBytes(StandardCharsets.UTF_8));
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.addCustomRules(invalid))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidStartDelay() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.beanDiscoveryDelay(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
