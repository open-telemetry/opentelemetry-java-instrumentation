/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.github.netmikey.logunit.api.LogCapturer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrTelemetryTest {

  @RegisterExtension LogCapturer logs = LogCapturer.create().captureForType(JfrTelemetry.class);

  private InMemoryMetricReader reader;
  private OpenTelemetrySdk sdk;

  @BeforeEach
  void setup() {
    reader = InMemoryMetricReader.createDelta();
    sdk =
        OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build())
            .build();
  }

  @AfterEach
  void tearDown() {
    sdk.getSdkMeterProvider().close();
  }

  @Test
  void create_Default() {
    try (JfrTelemetry unused = JfrTelemetry.create(sdk)) {
      assertThat(logs.getEvents()).hasSize(1);
      logs.assertContains("Starting JfrTelemetry");

      assertThat(reader.collectAllMetrics())
          .isNotEmpty()
          .allSatisfy(
              metric -> {
                assertThat(metric.getInstrumentationScopeInfo().getName())
                    .isEqualTo("io.opentelemetry.instrumentation.runtimemetricsjfr");
                //                assertThat(metric.getInstrumentationScopeInfo().getVersion())
                //                    .matches("1\\..*-alpha.*");
              });
      ;
    }
  }

  @Test
  void create_AllDisabled() {
    try (JfrTelemetry unused = JfrTelemetry.builder(sdk).disableAllFeatures().build()) {
      assertThat(logs.getEvents()).hasSize(1);
      logs.assertContains("Starting JfrTelemetry");

      assertThat(reader.collectAllMetrics()).isEmpty();
    }
  }

  @Test
  void builder() {
    try (var jfrTelemetry = JfrTelemetry.builder(sdk).build()) {
      assertThat(jfrTelemetry.getOpenTelemetry()).isSameAs(sdk);
      assertThat(jfrTelemetry.getRecordedEventHandlers())
          .hasSizeGreaterThan(0)
          .allSatisfy(handler -> assertThat(handler.getFeature().isDefaultEnabled()).isTrue());
    }
  }

  @Test
  void close() {
    try (JfrTelemetry jfrTelemetry = JfrTelemetry.create(sdk)) {
      // Track whether RecordingStream has been closed
      AtomicBoolean recordingStreamClosed = new AtomicBoolean(false);
      jfrTelemetry.getRecordingStream().onClose(() -> recordingStreamClosed.set(true));

      assertThat(reader.collectAllMetrics()).isNotEmpty();

      jfrTelemetry.close();
      logs.assertContains("Closing JfrTelemetry");
      logs.assertDoesNotContain("JfrTelemetry is already closed");
      assertThat(recordingStreamClosed.get()).isTrue();
      assertThat(reader.collectAllMetrics()).isEmpty();

      jfrTelemetry.close();
      logs.assertContains("JfrTelemetry is already closed");
    }
  }
}
