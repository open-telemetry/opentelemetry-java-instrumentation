/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.internal.ManualTelemetryEntry;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import org.junit.jupiter.api.Test;

class ManualTelemetryTest {

  @Test
  void testManualTelemetryParsing() throws JsonProcessingException {
    String yamlContent =
        """
        description: "Example instrumentation with manual telemetry documentation"
        semantic_conventions:
          - HTTP_CLIENT_SPANS
        library_link: https://example.com/library
        additional_telemetry:
        - when: default
          metrics:
          - name: system.disk.io
            description: System disk IO
            type: LONG_SUM
            unit: By
            attributes:
            - name: device
              type: STRING
            - name: direction
              type: STRING
          spans:
          - span_kind: CLIENT
            attributes:
            - name: custom.operation
              type: STRING
        - when: experimental
          metrics:
          - name: experimental.feature.usage
            description: Usage of experimental features
            type: HISTOGRAM
            unit: s
            attributes:
            - name: feature.name
              type: STRING
        """;

    InstrumentationMetadata metadata = YamlHelper.metaDataParser(yamlContent);

    assertThat(metadata).isNotNull();
    assertThat(metadata.getDescription())
        .isEqualTo("Example instrumentation with manual telemetry documentation");
    assertThat(metadata.getLibraryLink()).isEqualTo("https://example.com/library");
    assertThat(metadata.getOverrideTelemetry()).isFalse();

    assertThat(metadata.getAdditionalTelemetry()).hasSize(2);

    ManualTelemetryEntry defaultEntry = metadata.getAdditionalTelemetry().get(0);
    assertThat(defaultEntry.getWhen()).isEqualTo("default");
    assertThat(defaultEntry.getMetrics()).hasSize(1);
    assertThat(defaultEntry.getSpans()).hasSize(1);

    ManualTelemetryEntry.ManualMetric metric = defaultEntry.getMetrics().get(0);
    assertThat(metric.getName()).isEqualTo("system.disk.io");
    assertThat(metric.getDescription()).isEqualTo("System disk IO");
    assertThat(metric.getType()).isEqualTo("LONG_SUM");
    assertThat(metric.getUnit()).isEqualTo("By");
    assertThat(metric.getAttributes()).hasSize(2);

    ManualTelemetryEntry.ManualSpan span = defaultEntry.getSpans().get(0);
    assertThat(span.getSpanKind()).isEqualTo("CLIENT");
    assertThat(span.getAttributes()).hasSize(1);

    ManualTelemetryEntry experimentalEntry = metadata.getAdditionalTelemetry().get(1);
    assertThat(experimentalEntry.getWhen()).isEqualTo("experimental");
    assertThat(experimentalEntry.getMetrics()).hasSize(1);
    assertThat(experimentalEntry.getSpans()).isEmpty();
  }

  @Test
  void testOverrideTelemetryFlag() throws JsonProcessingException {
    String yamlContent =
        """
        description: "Example with override"
        override_telemetry: true
        additional_telemetry:
        - when: default
          metrics:
          - name: manual.metric
            description: Manual metric only
            type: COUNTER
            unit: "1"
            attributes: []
        """;

    InstrumentationMetadata metadata = YamlHelper.metaDataParser(yamlContent);

    assertThat(metadata).isNotNull();
    assertThat(metadata.getOverrideTelemetry()).isTrue();
    assertThat(metadata.getAdditionalTelemetry()).hasSize(1);
  }

  @Test
  void testEmptyAdditionalTelemetry() throws JsonProcessingException {
    String yamlContent =
        """
        description: "Example without manual telemetry"
        semantic_conventions:
          - HTTP_CLIENT_SPANS
        """;

    InstrumentationMetadata metadata = YamlHelper.metaDataParser(yamlContent);

    assertThat(metadata).isNotNull();
    assertThat(metadata.getOverrideTelemetry()).isFalse();
    assertThat(metadata.getAdditionalTelemetry()).isEmpty();
  }
}
