/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.exporter.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZipkinExporterRemovalCustomizerProviderTest {

  @Test
  void registeredViaServiceLoader() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.traces.exporter", "zipkin");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.logs.exporter", "none");
    properties.put("otel.instrumentation.common.v3-preview", "true");

    assertThatThrownBy(
            () ->
                AutoConfiguredOpenTelemetrySdk.builder()
                    .addPropertiesSupplier(() -> properties)
                    .build())
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("zipkin span exporter is not supported");
  }

  @Test
  void allowsZipkinWhenV3PreviewDisabled() {
    assertThatCode(() -> ZipkinExporterRemovalCustomizerProvider.customize(config("zipkin", false)))
        .doesNotThrowAnyException();
  }

  @Test
  void failsOnZipkinWhenV3PreviewEnabled() {
    assertThatThrownBy(
            () -> ZipkinExporterRemovalCustomizerProvider.customize(config("zipkin", true)))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("zipkin span exporter is not supported");
  }

  @Test
  void failsWhenZipkinIsOneOfSeveralExporters() {
    assertThatThrownBy(
            () -> ZipkinExporterRemovalCustomizerProvider.customize(config("otlp, zipkin", true)))
        .isInstanceOf(ConfigurationException.class);
  }

  @Test
  void allowsOtherExportersWhenV3PreviewEnabled() {
    assertThat(ZipkinExporterRemovalCustomizerProvider.customize(config("otlp", true))).isEmpty();
  }

  private static ConfigProperties config(String tracesExporter, boolean v3Preview) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.traces.exporter", tracesExporter);
    properties.put("otel.instrumentation.common.v3-preview", Boolean.toString(v3Preview));
    return DefaultConfigProperties.createFromMap(properties);
  }
}
