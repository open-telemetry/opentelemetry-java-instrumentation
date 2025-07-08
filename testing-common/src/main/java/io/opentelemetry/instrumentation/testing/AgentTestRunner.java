/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.test.utils.LoggerUtils;
import io.opentelemetry.instrumentation.testing.internal.MetaDataCollector;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link InstrumentationTestRunner} that delegates most of its calls to the
 * OpenTelemetry Javaagent that this process runs with. It uses the {@link
 * AgentTestingExporterAccess} bridge class to retrieve exported traces and metrics data from the
 * agent class loader.
 */
public final class AgentTestRunner extends InstrumentationTestRunner {
  static {
    try {
      LoggerUtils.setLevel(LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME), Level.WARN);
      LoggerUtils.setLevel(LoggerFactory.getLogger("io.opentelemetry"), Level.DEBUG);
    } catch (NoClassDefFoundError e) {
      // this happens when excluding logback in order to test slf4j -> log4j2
    }
  }

  private static final AgentTestRunner INSTANCE = new AgentTestRunner();

  public static InstrumentationTestRunner instance() {
    return INSTANCE;
  }

  private AgentTestRunner() {
    super(GlobalOpenTelemetry.get());
  }

  @Override
  public void beforeTestClass() {
    TestAgentListenerAccess.reset();
  }

  @Override
  public void afterTestClass() throws IOException {
    // Cleanup before assertion.
    assert TestAgentListenerAccess.getInstrumentationErrorCount() == 0
        : TestAgentListenerAccess.getInstrumentationErrorCount()
            + " Instrumentation errors during test";
    int adviceFailureCount = TestAgentListenerAccess.getAndResetAdviceFailureCount();
    assert adviceFailureCount == 0 : adviceFailureCount + " Advice failures during test";
    int muzzleFailureCount = TestAgentListenerAccess.getAndResetMuzzleFailureCount();
    assert muzzleFailureCount == 0 : muzzleFailureCount + " Muzzle failures during test";

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

    // additional library ignores are ignored during tests, because they can make it really
    // confusing for contributors wondering why their instrumentation is not applied,
    // but we then need to make sure that the additional library ignores won't then silently prevent
    // the instrumentation from being applied in real life outside of these tests
    assert TestAgentListenerAccess.getIgnoredButTransformedClassNames().isEmpty()
        : "Transformed classes match global libraries ignore matcher: "
            + TestAgentListenerAccess.getIgnoredButTransformedClassNames();
  }

  @Override
  public void clearAllExportedData() {
    AgentTestingExporterAccess.reset();
  }

  @Override
  public OpenTelemetry getOpenTelemetry() {
    return GlobalOpenTelemetry.get();
  }

  @Override
  public List<SpanData> getExportedSpans() {
    return AgentTestingExporterAccess.getExportedSpans();
  }

  @Override
  public List<MetricData> getExportedMetrics() {
    return AgentTestingExporterAccess.getExportedMetrics();
  }

  @Override
  public List<LogRecordData> getExportedLogRecords() {
    return AgentTestingExporterAccess.getExportedLogRecords();
  }

  @Override
  public boolean forceFlushCalled() {
    return AgentTestingExporterAccess.forceFlushCalled();
  }
}
