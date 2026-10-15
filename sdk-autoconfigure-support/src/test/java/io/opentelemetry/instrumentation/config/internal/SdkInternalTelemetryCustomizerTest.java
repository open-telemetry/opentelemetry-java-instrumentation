/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.instrumentation.testing.provider.TestMetricExporterComponentProvider;
import io.opentelemetry.instrumentation.testing.provider.TestSpanExporterComponentProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SdkInternalTelemetryCustomizerTest {

  @ParameterizedTest
  @ValueSource(strings = {"", "legacy", "latest"})
  void properties(String version) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.traces.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.logs.exporter", "none");
    if (!version.isEmpty()) {
      properties.put("otel.experimental.sdk.telemetry.version", version);
    }
    AutoConfiguredOpenTelemetrySdk configured =
        AutoConfiguredOpenTelemetrySdk.builder().addPropertiesSupplier(() -> properties).build();
    try (OpenTelemetrySdk sdk = configured.getOpenTelemetrySdk()) {
      assertThat(
              AutoConfigureUtil.getConfig(configured)
                  .getString("otel.experimental.sdk.telemetry.version"))
          .isEqualTo(version.isEmpty() ? "latest" : version);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "instrumentation/development:\n  java:\n    other:\n      enabled: true\n"})
  void declarativeMetrics(String instrumentation) {
    String yaml =
        "file_format: '1.2'\n"
            + "tracer_provider:\n"
            + "  processors:\n"
            + "    - batch:\n"
            + "        exporter:\n"
            + "          test:\n"
            + "logger_provider: {}\n"
            + "meter_provider:\n"
            + "  readers:\n"
            + "    - periodic:\n"
            + "        interval: 1000000\n"
            + "        exporter:\n"
            + "          test:\n"
            + instrumentation;
    InMemoryMetricExporter metrics = InMemoryMetricExporter.create();
    TestMetricExporterComponentProvider.setMetricExporter(metrics);
    TestSpanExporterComponentProvider.setSpanExporter(InMemorySpanExporter.create());
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    try (OpenTelemetrySdk sdk = DeclarativeConfiguration.create(model).getSdk()) {
      sdk.getTracer("test").spanBuilder("test").startSpan().end();
      sdk.getSdkTracerProvider().forceFlush().join(10, SECONDS);
      await()
          .untilAsserted(
              () -> {
                sdk.getSdkMeterProvider().forceFlush().join(10, SECONDS);
                assertThat(metrics.getFinishedMetricItems())
                    .extracting(MetricData::getName)
                    .contains("otel.sdk.processor.span.processed");
              });
      if (!instrumentation.isEmpty()) {
        assertThat(
                ((ExtendedOpenTelemetry) sdk)
                    .getConfigProvider()
                    .getInstrumentationConfig("other")
                    .getBoolean("enabled"))
            .isTrue();
      }
    } finally {
      TestSpanExporterComponentProvider.setSpanExporter(null);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"legacy", "latest"})
  void declarativeExplicitVersion(String version) {
    String yaml =
        "file_format: '1.2'\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    otel_sdk:\n"
            + "      internal_telemetry_version: "
            + version;
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    try (OpenTelemetrySdk sdk = DeclarativeConfiguration.create(model).getSdk()) {
      assertThat(
              ((ExtendedOpenTelemetry) sdk)
                  .getConfigProvider()
                  .getInstrumentationConfig("otel_sdk")
                  .getString("internal_telemetry_version"))
          .isEqualTo(version);
    }
  }
}
