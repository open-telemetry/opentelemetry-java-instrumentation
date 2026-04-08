/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.internal.ManualTelemetryEntry;
import io.opentelemetry.instrumentation.docs.internal.SemanticConvention;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.util.List;
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

    InstrumentationMetadata actualMetadata = YamlHelper.metaDataParser(yamlContent);
    ManualTelemetryEntry defaultEntry =
        new ManualTelemetryEntry(
            "default",
            List.of(
                new ManualTelemetryEntry.ManualMetric(
                    "system.disk.io",
                    "System disk IO",
                    "LONG_SUM",
                    "By",
                    List.of(
                        new TelemetryAttribute("device", "STRING"),
                        new TelemetryAttribute("direction", "STRING")))),
            List.of(
                new ManualTelemetryEntry.ManualSpan(
                    "CLIENT", List.of(new TelemetryAttribute("custom.operation", "STRING")))));

    ManualTelemetryEntry experimentalEntry =
        new ManualTelemetryEntry(
            "experimental",
            List.of(
                new ManualTelemetryEntry.ManualMetric(
                    "experimental.feature.usage",
                    "Usage of experimental features",
                    "HISTOGRAM",
                    "s",
                    List.of(new TelemetryAttribute("feature.name", "STRING")))),
            List.of());

    InstrumentationMetadata expectedMetadata =
        new InstrumentationMetadata.Builder()
            .description("Example instrumentation with manual telemetry documentation")
            .libraryLink("https://example.com/library")
            .semanticConventions(List.of(SemanticConvention.HTTP_CLIENT_SPANS))
            .additionalTelemetry(List.of(defaultEntry, experimentalEntry))
            .overrideTelemetry(false)
            .build();

    assertThat(actualMetadata).usingRecursiveComparison().isEqualTo(expectedMetadata);
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

    InstrumentationMetadata actualMetadata = YamlHelper.metaDataParser(yamlContent);

    ManualTelemetryEntry defaultEntry =
        new ManualTelemetryEntry(
            "default",
            List.of(
                new ManualTelemetryEntry.ManualMetric(
                    "manual.metric", "Manual metric only", "COUNTER", "1", List.of())),
            List.of());

    InstrumentationMetadata expectedMetadata =
        new InstrumentationMetadata.Builder()
            .description("Example with override")
            .overrideTelemetry(true)
            .additionalTelemetry(List.of(defaultEntry))
            .build();

    assertThat(actualMetadata).usingRecursiveComparison().isEqualTo(expectedMetadata);
  }

  @Test
  void testEmptyAdditionalTelemetry() throws JsonProcessingException {
    String yamlContent =
        """
        description: "Example without manual telemetry"
        semantic_conventions:
          - HTTP_CLIENT_SPANS
        """;

    InstrumentationMetadata actualMetadata = YamlHelper.metaDataParser(yamlContent);

    InstrumentationMetadata expectedMetadata =
        new InstrumentationMetadata.Builder()
            .description("Example without manual telemetry")
            .semanticConventions(List.of(SemanticConvention.HTTP_CLIENT_SPANS))
            .additionalTelemetry(emptyList())
            .overrideTelemetry(false)
            .build();

    assertThat(actualMetadata).usingRecursiveComparison().isEqualTo(expectedMetadata);
  }
}
