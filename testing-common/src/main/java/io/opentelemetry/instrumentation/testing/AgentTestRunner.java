/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link InstrumentationTestRunner} that delegates most of its calls to the
 * OpenTelemetry Javaagent that this process runs with. It uses the {@link
 * AgentTestingExporterAccess} bridge class to retrieve exported traces and metrics data from the
 * agent classloader.
 */
public final class AgentTestRunner implements InstrumentationTestRunner {
  static {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    ((Logger) LoggerFactory.getLogger("io.opentelemetry")).setLevel(Level.DEBUG);
  }

  private static final AgentTestRunner INSTANCE = new AgentTestRunner();

  public static InstrumentationTestRunner instance() {
    return INSTANCE;
  }

  @Override
  public void beforeTestClass() {
    TestAgentListenerAccess.reset();
  }

  @Override
  public void afterTestClass() {
    // Cleanup before assertion.
    assert TestAgentListenerAccess.getInstrumentationErrorCount() == 0
        : TestAgentListenerAccess.getInstrumentationErrorCount()
            + " Instrumentation errors during test";
    assert TestAgentListenerAccess.getIgnoredButTransformedClassNames().isEmpty()
        : "Transformed classes match global libraries ignore matcher: "
            + TestAgentListenerAccess.getIgnoredButTransformedClassNames();
  }

  @Override
  public void clearAllExportedData() {
    AgentTestingExporterAccess.reset();
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
  public boolean forceFlushCalled() {
    return AgentTestingExporterAccess.forceFlushCalled();
  }

  private AgentTestRunner() {}
}
