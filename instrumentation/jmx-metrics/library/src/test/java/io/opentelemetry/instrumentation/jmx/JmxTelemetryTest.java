/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import java.nio.file.Files;
import java.nio.file.Path;
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
    assertThatThrownBy(() -> builder.addClasspathRules("should-not-exist"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidExternalYaml(@TempDir Path dir) throws Exception {
    Path invalid = Files.createTempFile(dir, "invalid", ".yaml");
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.addCustomRules(invalid))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidStartDelay() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(OpenTelemetry.noop());
    assertThatThrownBy(() -> builder.beanDiscoveryDelay(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
