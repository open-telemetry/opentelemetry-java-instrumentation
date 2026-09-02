/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeclarativeConfigBridgeTest {
  @Test
  void testCreateInstrumentationConfig() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.instrumentation.http.known-methods", "GET,POST");

    ConfigProvider provider =
        DeclarativeConfigBridge.createInstrumentationConfig(
            DefaultConfigProperties.createFromMap(properties));

    DeclarativeConfigProperties config = provider.getInstrumentationConfig();

    assertThat(
            config
                .getStructured("java")
                .getStructured("common")
                .getStructured("http")
                .getString("known_methods"))
        .isEqualTo("GET,POST");
  }

  @Test
  void testCreateComponentProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.inferred.spans.enabled", "true");
    properties.put("otel.inferred.spans.backup.diagnostic.files", "true");
    properties.put("otel.inferred.spans.min.duration", "42ms");

    DeclarativeConfigProperties config =
        DeclarativeConfigBridge.createComponentProperties(
            DefaultConfigProperties.createFromMap(properties), "otel.inferred.spans.");

    assertThat(config.getBoolean("enabled")).isTrue();
    assertThat(config.getBoolean("backup_diagnostic_files")).isTrue();
    assertThat(DeclarativeConfigDurationUtil.getDuration(config, "min_duration"))
        .isEqualTo(Duration.ofMillis(42));
  }
}
