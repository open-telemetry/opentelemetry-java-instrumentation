/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test

import static org.awaitility.Awaitility.await

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.trace.data.SpanData
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import org.junit.Rule
import org.junit.rules.Timeout
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/**
 * Base class for test specifications that are shared between instrumentation libraries and agent.
 * The methods in this class are implemented by {@link AgentTestTrait} and
 * {@link LibraryTestTrait}.
 */
abstract class InstrumentationSpecification extends Specification {
  abstract InstrumentationTestRunner testRunner()

  @Rule
  public Timeout testTimeout = new Timeout(10, TimeUnit.MINUTES)

  def setupSpec() {
    testRunner().beforeTestClass()
  }

  /**
   * Clears all data exported during a test.
   */
  def setup() {
    assert !Span.current().getSpanContext().isValid(): "Span is active before test has started: " + Span.current()
    testRunner().clearAllExportedData()
  }

  def cleanup() {
    ContextRestorer restorer = ContextRestorer.create()
    if (restorer == null) {
      tryCleanup()
      return
    }

    // If close is called before all request processing threads have completed we might get false
    // positive notifications for leaked scopes when strict context checks are enabled. Here we
    // retry close when scope leak was reported.
    await()
      .ignoreException(AssertionError)
      .atMost(15, TimeUnit.SECONDS)
      .until({
        restorer.runWithRestore { tryCleanup() }
        true
      })
  }

  def tryCleanup() {
    ContextStorage storage = ContextStorage.get()
    if (storage instanceof AutoCloseable) {
      ((AutoCloseable) storage).close()
    }
  }

  // Helper class that allows for retrying ContextStorage close operation by restoring ContextStorage
  // to the state where it was before close was called.
  static abstract class ContextRestorer {
    abstract void restore()

    void runWithRestore(Closure target) {
      try {
        target.run()
      } catch (AssertionError assertionError) {
        restore()
        throw assertionError
      }
    }

    static ContextRestorer create() {
      def strictContextStorage = getStrictContextStorage()
      if (strictContextStorage == null) {
        return null
      }
      def pendingScopes = getStrictContextStoragePendingScopes(strictContextStorage)

      def pendingScopesClass = Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.StrictContextStorage\$PendingScopes")
      Field mapField = pendingScopesClass.getDeclaredField("map")
      mapField.setAccessible(true)
      ConcurrentHashMap map = mapField.get(pendingScopes)
      Map copy = new HashMap(map)

      return new ContextRestorer() {
        @Override
        void restore() {
          map.putAll(copy)
        }
      }
    }

    static getStrictContextStoragePendingScopes(def strictContextStorage) {
      def strictContextStorageClass = Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.StrictContextStorage")
      Field field = strictContextStorageClass.getDeclaredField("pendingScopes")
      field.setAccessible(true)
      return field.get(strictContextStorage)
    }

    static getStrictContextStorage() {
      def contextStorage = getAgentContextStorage()
      if (contextStorage == null) {
        return null
      }
      contextStorage = unwrapStrictContextStressor(contextStorage)
      def contextStorageClass = contextStorage.getClass()
      if (contextStorageClass.getName().contains("StrictContextStorage")) {
        return contextStorage
      }
      return null
    }

    static getAgentContextStorage() {
      try {
        def contextStorageClass = Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.ContextStorage")
        def method = contextStorageClass.getDeclaredMethod("get")
        return method.invoke(null)
      } catch (Exception exception) {
        return null
      }
    }

    static unwrapStrictContextStressor(def contextStorage) {
      Class<?> contextStorageClass = contextStorage.getClass()
      if (contextStorageClass.getName().contains("StrictContextStressor")) {
        Field field = contextStorageClass.getDeclaredField("contextStorage")
        field.setAccessible(true)
        return field.get(contextStorage)
      }
      return contextStorage
    }
  }

  def cleanupSpec() {
    testRunner().afterTestClass()
  }

  /** Return the {@link OpenTelemetry} instance used to produce telemetry data. */
  OpenTelemetry getOpenTelemetry() {
    testRunner().openTelemetry
  }

  /** Return a list of all captured traces, where each trace is a sorted list of spans. */
  List<List<SpanData>> getTraces() {
    TelemetryDataUtil.groupTraces(testRunner().getExportedSpans())
  }

  /** Return a list of all captured metrics. */
  List<MetricData> getMetrics() {
    testRunner().getExportedMetrics()
  }

  /**
   * Removes all captured telemetry data. After calling this method {@link #getTraces()} and
   * {@link #getMetrics()} will return empty lists until more telemetry data is captured.
   */
  void clearExportedData() {
    testRunner().clearAllExportedData()
  }

  boolean forceFlushCalled() {
    return testRunner().forceFlushCalled()
  }

  /**
   * Wait until at least {@code numberOfTraces} traces are completed and return all captured traces.
   * Note that there may be more than {@code numberOfTraces} collected. By default this waits up to
   * 20 seconds, then times out.
   */
  List<List<SpanData>> waitForTraces(int numberOfTraces) {
    TelemetryDataUtil.waitForTraces({ testRunner().getExportedSpans() }, numberOfTraces)
  }

  void ignoreTracesAndClear(int numberOfTraces) {
    waitForTraces(numberOfTraces)
    clearExportedData()
  }

  void assertTraces(
    final int size,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    InMemoryExporterAssert.assertTraces({ testRunner().getExportedSpans() }, size, spec)
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  def <T> T runWithSpan(String spanName, Closure callback) {
    return (T) testRunner().runWithSpan(spanName, (ThrowingSupplier) callback)
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  def <T> T runWithClientSpan(String spanName, Closure callback) {
    return (T) testRunner().runWithClientSpan(spanName, (ThrowingSupplier) callback)
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  def <T> T runWithServerSpan(String spanName, Closure callback) {
    return (T) testRunner().runWithServerSpan(spanName, (ThrowingSupplier) callback)
  }
}
