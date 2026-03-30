/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.github.netmikey.logunit.api.LogCapturer;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.jfr.FlightRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RuntimeTelemetryTest {

  @RegisterExtension LogCapturer logs = LogCapturer.create().captureForType(RuntimeTelemetry.class);

  private InMemoryMetricReader reader;
  private OpenTelemetrySdk sdk;

  @BeforeEach
  void setup() {
    try {
      Class.forName("jdk.jfr.FlightRecorder");
    } catch (ClassNotFoundException exception) {
      Assumptions.abort("JFR not present");
    }
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");

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
    try (RuntimeTelemetry unused = RuntimeTelemetry.create(sdk)) {
      assertThat(reader.collectAllMetrics())
          .isNotEmpty()
          .allSatisfy(
              metric -> {
                assertThat(metric.getInstrumentationScopeInfo().getName())
                    .isEqualTo("io.opentelemetry.runtime-telemetry");
              });
    }
  }

  @Test
  void builder_DefaultNoJfr() {
    // By default, no JFR features are enabled because all features either overlap
    // with JMX or are experimental
    try (var runtimeTelemetry = RuntimeTelemetry.builder(sdk).build()) {
      assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
    }
  }

  @Test
  void builder_WithFeatureEnabled() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(sdk);
    builder.getJfrConfig().enableFeature(JfrFeature.LOCK_METRICS);
    try (var runtimeTelemetry = builder.build()) {
      JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
          (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
      assertThat(jfrRuntimeMetrics).isNotNull();
      assertThat(jfrRuntimeMetrics.getRecordedEventHandlers())
          .hasSizeGreaterThan(0)
          .allSatisfy(
              handler -> {
                assertThat(handler.getFeature()).isEqualTo(JfrFeature.LOCK_METRICS);
              });
    }
  }

  @Test
  void close() throws InterruptedException {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(sdk);
    // Enable a feature to test close behavior with JFR
    builder.getJfrConfig().enableFeature(JfrFeature.LOCK_METRICS);
    try (RuntimeTelemetry jfrTelemetry = builder.build()) {
      JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
          (JfrConfig.JfrRuntimeMetrics) jfrTelemetry.getJfrTelemetry();

      // Track whether RecordingStream has been closed
      AtomicBoolean recordingStreamClosed = new AtomicBoolean(false);
      jfrRuntimeMetrics.getRecordingStream().onClose(() -> recordingStreamClosed.set(true));

      assertThat(reader.collectAllMetrics()).isNotEmpty();

      jfrTelemetry.close();
      logs.assertDoesNotContain("RuntimeTelemetry is already closed");
      assertThat(recordingStreamClosed.get()).isTrue();

      // clear all metrics that might have arrived after close
      Thread.sleep(100); // give time for any inflight metric export to be received
      reader.collectAllMetrics();

      Thread.sleep(100);
      assertThat(reader.collectAllMetrics()).isEmpty();

      jfrTelemetry.close();
      logs.assertContains("RuntimeTelemetry is already closed");
    }
  }
}
