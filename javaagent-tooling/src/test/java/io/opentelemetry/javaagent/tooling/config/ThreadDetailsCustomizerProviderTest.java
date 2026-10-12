/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;

import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThreadDetailsCustomizerProviderTest {

  @BeforeEach
  @AfterEach
  void resetDistributionConfig() {
    AgentDistributionConfig.resetForTest();
  }

  @ParameterizedTest
  @CsvSource(
      value = {"NULL, false", "true, true", "false, false"},
      nullValues = "NULL")
  void flatConfig(String value, boolean expected) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.traces.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.logs.exporter", "none");
    if (value != null) {
      properties.put("otel.javaagent.add-thread-details", value);
    }

    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    try (OpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(() -> properties)
            .addTracerProviderCustomizer(
                (builder, config) -> builder.addSpanProcessor(SimpleSpanProcessor.create(exporter)))
            .build()
            .getOpenTelemetrySdk()) {
      sdk.getTracer("test").spanBuilder("test").startSpan().end();

      assertThat(exporter.getFinishedSpanItems())
          .singleElement()
          .satisfies(
              span ->
                  assertThat(span)
                      .hasAttributesSatisfyingExactly(
                          equalTo(THREAD_ID, expected ? Thread.currentThread().getId() : null),
                          equalTo(
                              THREAD_NAME, expected ? Thread.currentThread().getName() : null)));
    }
  }
}
