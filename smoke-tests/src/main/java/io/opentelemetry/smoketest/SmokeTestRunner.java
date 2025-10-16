/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.instrumentation.testing.internal.MetaDataCollector;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

/**
 * An implementation of {@link InstrumentationTestRunner} that uses {@link TelemetryRetriever} to
 * fetch traces, metrics and logs from the fake backend.
 */
public class SmokeTestRunner extends InstrumentationTestRunner {

  private static final SmokeTestRunner INSTANCE = new SmokeTestRunner();

  private TelemetryRetriever telemetryRetriever;

  public static SmokeTestRunner instance() {
    return INSTANCE;
  }

  private SmokeTestRunner() {
    super(OpenTelemetry.noop());
  }

  void setTelemetryRetriever(TelemetryRetriever telemetryRetriever) {
    this.telemetryRetriever = telemetryRetriever;
  }

  @Override
  public void beforeTestClass() {}

  @Override
  public void afterTestClass() throws IOException {
    // Generates files in a `.telemetry` directory within the instrumentation module with all
    // captured emitted metadata to be used by the instrumentation-docs Doc generator.
    if (Boolean.getBoolean("collectMetadata")) {
      URL resource = this.getClass().getClassLoader().getResource("");
      if (resource == null) {
        return;
      }
      String path = Paths.get(resource.getPath()).toString();

      MetaDataCollector.writeTelemetryToFiles(path, metricsByScope, tracesByScope);
    }
  }

  @Override
  public void clearAllExportedData() {
    if (telemetryRetriever != null) {
      telemetryRetriever.clearTelemetry();
    }
  }

  @Override
  public OpenTelemetry getOpenTelemetry() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SpanData> getExportedSpans() {
    return telemetryRetriever.waitForTraces();
  }

  @Override
  public List<MetricData> getExportedMetrics() {
    return telemetryRetriever.waitForMetrics();
  }

  @Override
  public List<LogRecordData> getExportedLogRecords() {
    return telemetryRetriever.waitForLogs();
  }

  @Override
  public boolean forceFlushCalled() {
    throw new UnsupportedOperationException();
  }
}
