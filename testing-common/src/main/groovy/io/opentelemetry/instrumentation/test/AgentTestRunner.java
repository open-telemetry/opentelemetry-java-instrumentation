/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import org.slf4j.LoggerFactory;

public final class AgentTestRunner {

  static {
    // always run with the thread propagation debugger to help track down sporadic test failures
    System.setProperty("otel.threadPropagationDebugger", "true");
    System.setProperty("otel.internal.failOnContextLeak", "true");
    // always print muzzle warnings
    System.setProperty("io.opentelemetry.javaagent.slf4j.simpleLogger.log.muzzleMatcher", "warn");
  }

  /**
   * For test runs, agent's global tracer will report to this list writer.
   *
   * <p>Before the start of each test the reported traces will be reset.
   */
  public static final InMemoryExporter TEST_WRITER = new InMemoryExporter();

  static {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    ((Logger) LoggerFactory.getLogger("io.opentelemetry")).setLevel(Level.DEBUG);
  }

  public void setupBeforeTests() {
    TestAgentListenerAccess.reset();
  }

  public void beforeTest() {
    assert !Span.current().getSpanContext().isValid()
        : "Span is active before test has started: " + Span.current();
    AgentTestingExporterAccess.reset();
  }

  public static synchronized void agentCleanup() {
    // Cleanup before assertion.
    assert TestAgentListenerAccess.getInstrumentationErrorCount() == 0
        : TestAgentListenerAccess.getInstrumentationErrorCount()
            + " Instrumentation errors during test";
    assert TestAgentListenerAccess.getIgnoredButTransformedClassNames().isEmpty()
        : "Transformed classes match global libraries ignore matcher: "
            + TestAgentListenerAccess.getIgnoredButTransformedClassNames();
  }

  public static void assertTraces(
      int size,
      @ClosureParams(
              value = SimpleType.class,
              options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
          @DelegatesTo(value = InMemoryExporterAssert.class, strategy = Closure.DELEGATE_FIRST)
          Closure spec) {
    InMemoryExporterAssert.assertTraces(AgentTestingExporterAccess::getExportedSpans, size, spec);
  }
}
