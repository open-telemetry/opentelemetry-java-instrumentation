/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.trace.ReadableSpan;
import java.io.ByteArrayInputStream;
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

    try (OpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(() -> properties)
            .build()
            .getOpenTelemetrySdk()) {
      assertThreadAttributes(sdk, expected);
    }
  }

  @ParameterizedTest
  @CsvSource(
      value = {"NULL, false", "true, true", "false, false"},
      nullValues = "NULL")
  void declarativeConfig(String value, boolean expected) {
    String yaml =
        "file_format: \"1.1\"\n"
            + (value == null
                ? ""
                : "distribution:\n  javaagent:\n    thread_details_enabled: " + value + "\n");

    try (OpenTelemetrySdk sdk =
        DeclarativeConfiguration.parseAndCreate(new ByteArrayInputStream(yaml.getBytes(UTF_8)))
            .getSdk()) {
      assertThat(AgentDistributionConfig.get().isThreadDetailsEnabled()).isEqualTo(expected);
      assertThreadAttributes(sdk, expected);
    }
  }

  private static void assertThreadAttributes(OpenTelemetrySdk sdk, boolean expected) {
    Span span = sdk.getTracer("test").spanBuilder("test").startSpan();
    try {
      Attributes attributes = ((ReadableSpan) span).toSpanData().getAttributes();
      assertThat(attributes.get(THREAD_ID))
          .isEqualTo(expected ? Thread.currentThread().getId() : null);
      assertThat(attributes.get(THREAD_NAME))
          .isEqualTo(expected ? Thread.currentThread().getName() : null);
    } finally {
      span.end();
    }
  }
}
