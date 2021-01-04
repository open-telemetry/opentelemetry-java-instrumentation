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
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
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

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(AgentTestRunner.class);

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

  protected static final Tracer TEST_TRACER;

  static {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    ((Logger) LoggerFactory.getLogger("io.opentelemetry")).setLevel(Level.DEBUG);

    TEST_TRACER = OpenTelemetry.getGlobalTracer("io.opentelemetry.auto");
  }

  protected static Tracer getTestTracer() {
    return TEST_TRACER;
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

  /**
   * This is used by setupSpec() methods to auto-retry setup that depends on finding and then using
   * an available free port, because that kind of setup can fail sporadically if the available port
   * gets re-used between when we find the available port and when we use it.
   *
   * @param closure the groovy closure to run with retry
   */
  public static void withRetryOnAddressAlreadyInUse(Closure<?> closure) {
    withRetryOnAddressAlreadyInUse(closure, 3);
  }

  private static void withRetryOnAddressAlreadyInUse(Closure<?> closure, int numRetries) {
    try {
      closure.call();
    } catch (Throwable t) {
      // typically this is "java.net.BindException: Address already in use", but also can be
      // "io.netty.channel.unix.Errors$NativeIoException: bind() failed: Address already in use"
      if (numRetries == 0 || !t.getMessage().contains("Address already in use")) {
        throw t;
      }
      log.debug("retrying due to bind exception: {}", t.getMessage(), t);
      withRetryOnAddressAlreadyInUse(closure, numRetries - 1);
    }
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

  protected static String getClassName(Class clazz) {
    String className = clazz.getSimpleName();
    if (className.isEmpty()) {
      className = clazz.getName();
      if (clazz.getPackage() != null) {
        String pkgName = clazz.getPackage().getName();
        if (!pkgName.isEmpty()) {
          className = clazz.getName().replace(pkgName, "").substring(1);
        }
      }
    }
    return className;
  }
}
