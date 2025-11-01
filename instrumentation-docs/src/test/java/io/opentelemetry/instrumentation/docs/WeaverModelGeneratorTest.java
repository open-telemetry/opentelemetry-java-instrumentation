/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeaverModelGeneratorTest {

  EmittedMetrics.Metric metric =
      new EmittedMetrics.Metric(
          "http.server.request.duration",
          "Duration of HTTP server requests.",
          "HISTOGRAM",
          "s",
          List.of(
              new TelemetryAttribute("http.request.method", "STRING"),
              new TelemetryAttribute("http.response.status_code", "LONG"),
              new TelemetryAttribute("url.scheme", "STRING"),
              new TelemetryAttribute("network.protocol.version", "STRING")));

  InstrumentationMetadata metadata =
      new InstrumentationMetadata.Builder().displayName("ActiveJ").build();

  InstrumentationModule module =
      new InstrumentationModule.Builder()
          .namespace("activej")
          .group("activej")
          .srcPath("instrumentation/activej-http-6.0")
          .instrumentationName("activej-http-6.0")
          .metadata(metadata)
          .metrics(Map.of("default", List.of(metric)))
          .build();

  @Test
  void testGenerateSignals() throws IOException {
    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    WeaverModelGenerator.generateSignals(module, writer);
    writer.flush();

    String expectedYaml =
        """
          # This file is generated and should not be manually edited.
          groups:
            - id: metric."http.server.request.duration"
              type: metric
              metric_name: "http.server.request.duration"
              stability: development
              brief: "Duration of HTTP server requests."
              instrument: "histogram"
              unit: "s"
              attributes:
                - ref: "http.request.method"
                - ref: "http.response.status_code"
                - ref: "url.scheme"
                - ref: "network.protocol.version"
          """;

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }

  @Test
  void testGenerateRegistry() throws IOException {
    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    WeaverModelGenerator.generateManifest(module, writer);
    writer.flush();

    String expectedYaml =
        """
            # This file is generated and should not be manually edited.
            name: "activej-http-6.0"
            description: "activej-http-6.0 Semantic Conventions"
            semconv_version: 0.1.0
            schema_base_url: https://weaver-example.io/schemas/
            dependencies:
              - name: otel
                registry_path: https://github.com/open-telemetry/semantic-conventions/archive/refs/tags/v1.34.0.zip[model]""";

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }

  @Test
  void testGenerateAttributes() throws IOException {
    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    Set<String> attributes = WeaverModelGenerator.getMetricAttributes(module);
    WeaverModelGenerator.generateAttributes(module, writer, attributes);
    writer.flush();

    String expectedYaml =
        """
          # This file is generated and should not be manually edited.
          groups:
            - id: registry.activej-http-6.0
              type: attribute_group
              display_name: ActiveJ Attributes
              brief: Attributes captured by ActiveJ instrumentation.
              attributes:
                - ref: http.request.method
                - ref: http.response.status_code
                - ref: network.protocol.version
                - ref: url.scheme
          """;

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }
}
