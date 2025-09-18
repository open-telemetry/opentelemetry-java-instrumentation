package io.opentelemetry.smoketest;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;

public class SmokeTestRunner extends InstrumentationTestRunner {

  private static final SmokeTestRunner INSTANCE = new SmokeTestRunner();

  private JavaTelemetryRetriever telemetryRetriever;

  public static SmokeTestRunner instance() {
    return INSTANCE;
  }

  private SmokeTestRunner() {
    super(OpenTelemetry.noop());
  }

  void setTelemetryRetriever(JavaTelemetryRetriever telemetryRetriever) {
    this.telemetryRetriever = telemetryRetriever;
  }

  @Override
  public void beforeTestClass() {
  }

  @Override
  public void afterTestClass() {
  }

  @Override
  public void clearAllExportedData() {
    telemetryRetriever.clearTelemetry();
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
