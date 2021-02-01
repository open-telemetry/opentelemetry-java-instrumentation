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
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.model.SpecMetadata;
import spock.lang.Specification;

/**
 * A spock test runner which automatically applies instrumentation and exposes a global trace
 * writer.
 *
 * <p>To use, write a regular spock test, but extend this class instead of {@link
 * spock.lang.Specification}.
 */
@SpecMetadata(filename = "AgentTestRunner.java", line = 0)
public abstract class AgentTestRunner extends Specification {

  static {
    // always run with the thread propagation debugger to help track down sporadic test failures
    System.setProperty("otel.threadPropagationDebugger", "true");
    System.setProperty("otel.internal.failOnContextLeak", "true");
    // always print muzzle warnings
    System.setProperty("io.opentelemetry.javaagent.slf4j.simpleLogger.log.muzzleMatcher", "warn");
  }

  protected static final Tracer TEST_TRACER;

  static {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    ((Logger) LoggerFactory.getLogger("io.opentelemetry")).setLevel(Level.DEBUG);
  }

  /**
   * Returns conditions for the classname for a class for which transformation should be skipped.
   */
  protected List<Function<String, Boolean>> skipTransformationConditions() {
    return Collections.emptyList();
  }

  /**
   * Returns conditions for the classname for a class and throwable of an error for which errors
   * should be ignored.
   */
  protected List<BiFunction<String, Throwable, Boolean>> skipErrorConditions() {
    return Collections.emptyList();
  }

  /**
   * Normally {@code @BeforeClass} is run only on static methods, but spock allows us to run it on
   * instance methods. Note: this means there is a 'special' instance of test class that is not used
   * to run any tests, but instead is just used to run this method once.
   */
  @BeforeClass
  public void setupBeforeTests() {
    TestAgentListenerAccess.reset();
    skipTransformationConditions().forEach(TestAgentListenerAccess::addSkipTransformationCondition);
    skipErrorConditions().forEach(TestAgentListenerAccess::addSkipErrorCondition);
  }

  @Before
  public void beforeTest() {
    assert !Span.current().getSpanContext().isValid()
        : "Span is active before test has started: " + Span.current();
    AgentTestingExporterAccess.reset();
  }

  @AfterClass
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
