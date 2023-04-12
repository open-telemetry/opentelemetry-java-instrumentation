/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetricsjava17;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.github.netmikey.logunit.api.LogCapturer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RuntimeMetricsTest {

  @RegisterExtension LogCapturer logs = LogCapturer.create().captureForType(RuntimeMetrics.class);

  private InMemoryMetricReader reader;
  private OpenTelemetrySdk sdk;

  @BeforeEach
  void setup() {
    try {
      Class.forName("jdk.jfr.consumer.RecordingStream");
    } catch (ClassNotFoundException exception) {
      Assumptions.abort("JFR not present");
    }

    reader = InMemoryMetricReader.createDelta();
    sdk =
        OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build())
            .build();
  }

  @AfterEach
  void tearDown() {
    if (sdk != null) {
      sdk.getSdkMeterProvider().close();
    }
  }

  @Test
  void create_Default() {
    try (RuntimeMetrics unused = RuntimeMetrics.create(sdk)) {
      assertThat(reader.collectAllMetrics())
          .isNotEmpty()
          .allSatisfy(
              metric -> {
                assertThat(metric.getInstrumentationScopeInfo().getName())
                    .isEqualTo("io.opentelemetry.instrumentation.runtime-metrics");
              });
    }
  }

  @Test
  void create_AllDisabled() {
    try (RuntimeMetrics unused = RuntimeMetrics.builder(sdk).disableAllMetrics().build()) {
      assertThat(reader.collectAllMetrics()).isEmpty();
    }
  }

  @Test
  void builder() {
    try (var jfrTelemetry = RuntimeMetrics.builder(sdk).build()) {
      assertThat(jfrTelemetry.getOpenTelemetry()).isSameAs(sdk);
      assertThat(jfrTelemetry.getRecordedEventHandlers())
          .hasSizeGreaterThan(0)
          .allSatisfy(handler -> assertThat(handler.getFeature().isDefaultEnabled()).isTrue());
    }
  }

  @Test
  void close() {
    try (RuntimeMetrics jfrTelemetry = RuntimeMetrics.builder(sdk).build()) {
      // Track whether RecordingStream has been closed
      AtomicBoolean recordingStreamClosed = new AtomicBoolean(false);
      jfrTelemetry.getRecordingStream().onClose(() -> recordingStreamClosed.set(true));

      assertThat(reader.collectAllMetrics()).isNotEmpty();

      jfrTelemetry.close();
      logs.assertDoesNotContain("RuntimeMetrics is already closed");
      assertThat(recordingStreamClosed.get()).isTrue();
      assertThat(reader.collectAllMetrics())
          .allSatisfy(
              metric -> {
                assertThat(metric.getName()).isEqualTo("process.runtime.jvm.gc.duration");
              });

      jfrTelemetry.close();
      logs.assertContains("RuntimeMetrics is already closed");
    }
  }
}
