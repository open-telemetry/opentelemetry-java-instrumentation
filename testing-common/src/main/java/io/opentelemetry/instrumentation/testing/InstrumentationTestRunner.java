/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.internal.InternalAttributeKeyImpl;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.CollectedEvent;
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.LogRecordDataAssert;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.assertj.core.api.ListAssert;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;

/**
 * This interface defines a common set of operations for interaction with OpenTelemetry SDK and
 * traces & metrics exporters.
 *
 * @see LibraryTestRunner
 * @see AgentTestRunner
 */
public abstract class InstrumentationTestRunner {

  private static final String EVENT_NAME_ATTRIBUTE_KEY = "event.name";
  private static final AttributeKey<String> EVENT_NAME_ATTRIBUTE =
      AttributeKey.stringKey(EVENT_NAME_ATTRIBUTE_KEY);

  private final OpenTelemetry openTelemetry;
  // Lazy initialized so that test runners can load without triggering Instrumenter construction
  // in the OpenTelemetry API bridging tests where some of the newer OpenTelemetry APIs used by
  // Instrumenter are absent.
  @Nullable private TestInstrumenters testInstrumenters;
  protected Map<InstrumentationScopeInfo, Map<String, MetricData>> metricsByScope = new HashMap<>();
  protected Set<InstrumentationScopeInfo> instrumentationScopes = new HashSet<>();

  /**
   * Stores traces by scope, where each scope contains a map of span kinds to a map of attribute
   * keys to their types. This is used to collect metadata about the spans emitted during tests.
   */
  protected Map<
          InstrumentationScopeInfo, Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>>>
      tracesByScope = new HashMap<>();

  /**
   * Stores events by scope, where each scope contains a map of event identities (name and severity)
   * to the accumulated shape of that event. This is used to collect metadata about the events
   * emitted during tests.
   */
  protected Map<InstrumentationScopeInfo, Map<CollectedEvent.Key, CollectedEvent>> eventsByScope =
      new HashMap<>();

  protected InstrumentationTestRunner(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public abstract void beforeTestClass();

  public abstract void afterTestClass() throws IOException;

  public abstract void clearAllExportedData();

  public abstract OpenTelemetry getOpenTelemetry();

  public abstract List<SpanData> getExportedSpans();

  public abstract List<MetricData> getExportedMetrics();

  public abstract List<LogRecordData> getExportedLogRecords();

  public abstract boolean forceFlushCalled();

  /**
   * Returns the peer service name expected on spans that target a local test server, or {@code
   * null} when no peer service is expected.
   *
   * <p>Peer service mapping is a javaagent-only feature, so only javaagent tests expect a value.
   * Library instrumentation never applies the peer service extractor; library users add peer
   * service through their own attributes extractor.
   */
  @Nullable
  public String expectedPeerService() {
    return null;
  }

  /** Return a list of all captured traces, where each trace is a sorted list of spans. */
  public List<List<SpanData>> traces() {
    return TelemetryDataUtil.groupTraces(getExportedSpans());
  }

  public List<List<SpanData>> waitForTraces(int numberOfTraces) {
    try {
      return TelemetryDataUtil.waitForTraces(this::getExportedSpans, numberOfTraces, 20, SECONDS);
    } catch (TimeoutException | InterruptedException e) {
      throw new AssertionError("Error waiting for " + numberOfTraces + " traces", e);
    }
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertSortedTraces(
      Comparator<List<SpanData>> traceComparator, Consumer<TraceAssert>... assertions) {
    waitAndAssertTraces(traceComparator, asList(assertions), true);
  }

  public void waitAndAssertSortedTraces(
      Comparator<List<SpanData>> traceComparator,
      Iterable<? extends Consumer<TraceAssert>> assertions) {
    waitAndAssertTraces(traceComparator, assertions, true);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertTracesWithoutScopeVersionVerification(
      Consumer<TraceAssert>... assertions) {
    waitAndAssertTracesWithoutScopeVersionVerification(asList(assertions));
  }

  public <T extends Consumer<TraceAssert>> void waitAndAssertTracesWithoutScopeVersionVerification(
      Iterable<T> assertions) {
    waitAndAssertTraces(null, assertions, false);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertTraces(Consumer<TraceAssert>... assertions) {
    waitAndAssertTraces(asList(assertions));
  }

  public <T extends Consumer<TraceAssert>> void waitAndAssertTraces(Iterable<T> assertions) {
    waitAndAssertTraces(null, assertions, true);
  }

  private <T extends Consumer<TraceAssert>> void waitAndAssertTraces(
      @Nullable Comparator<List<SpanData>> traceComparator,
      Iterable<T> assertions,
      boolean verifyScopeVersion) {
    List<T> assertionsList = new ArrayList<>();
    assertions.forEach(assertionsList::add);

    awaitUntilAsserted(() -> doAssertTraces(traceComparator, assertionsList, verifyScopeVersion));
  }

  private <T extends Consumer<TraceAssert>> void doAssertTraces(
      @Nullable Comparator<List<SpanData>> traceComparator,
      List<T> assertionsList,
      boolean verifyScopeVersion) {
    List<List<SpanData>> traces = waitForTraces(assertionsList.size());
    if (verifyScopeVersion) {
      TelemetryDataUtil.assertScopeVersion(traces);
    }
    if (traceComparator != null) {
      traces.sort(traceComparator);
    }
    TracesAssert.assertThat(traces).hasTracesSatisfyingExactly(assertionsList);

    if (Boolean.getBoolean("collectMetadata")) {
      collectEmittedSpans(traces);
    }
  }

  /**
   * Waits for the assertion applied to all metrics of the given instrumentation and metric name to
   * pass.
   */
  public void waitAndAssertMetrics(
      String instrumentationName, String metricName, Consumer<ListAssert<MetricData>> assertion) {

    awaitUntilAsserted(
        () ->
            assertion.accept(
                assertThat(getExportedMetrics())
                    .describedAs(
                        "Metrics for instrumentation %s and metric name %s",
                        instrumentationName, metricName)
                    .filteredOn(
                        data ->
                            data.getInstrumentationScopeInfo().getName().equals(instrumentationName)
                                && data.getName().equals(metricName))));

    TelemetryDataUtil.assertMetricScopeVersion(metricsForScope(instrumentationName));

    if (Boolean.getBoolean("collectMetadata")) {
      collectEmittedMetrics(getExportedMetrics());
    }
  }

  @SafeVarargs
  public final void waitAndAssertMetrics(
      String instrumentationName, Consumer<MetricAssert>... assertions) {
    awaitUntilAsserted(
        () -> {
          Collection<MetricData> metrics = instrumentationMetrics(instrumentationName);
          assertThat(metrics).isNotEmpty();
          for (int i = 0; i < assertions.length; i++) {
            int index = i;
            assertThat(metrics)
                .describedAs(
                    "Metrics for instrumentation %s and assertion %d", instrumentationName, index)
                .anySatisfy(metric -> assertions[index].accept(assertThat(metric)));
          }
        });

    TelemetryDataUtil.assertMetricScopeVersion(metricsForScope(instrumentationName));

    if (Boolean.getBoolean("collectMetadata")) {
      collectEmittedMetrics(getExportedMetrics());
    }
  }

  private List<MetricData> metricsForScope(String instrumentationName) {
    return getExportedMetrics().stream()
        .filter(m -> m.getInstrumentationScopeInfo().getName().equals(instrumentationName))
        .collect(toList());
  }

  private void collectEmittedMetrics(List<MetricData> metrics) {
    for (MetricData metric : metrics) {
      Map<String, MetricData> scopeMap =
          this.metricsByScope.computeIfAbsent(
              metric.getInstrumentationScopeInfo(), m -> new HashMap<>());

      if (!scopeMap.containsKey(metric.getName())) {
        scopeMap.put(metric.getName(), metric);
      }

      InstrumentationScopeInfo scopeInfo = metric.getInstrumentationScopeInfo();
      if (!scopeInfo.getName().equals("test")) {
        instrumentationScopes.add(scopeInfo);
      }
    }
  }

  private void collectEmittedSpans(List<List<SpanData>> spans) {
    for (List<SpanData> spanList : spans) {
      for (SpanData span : spanList) {
        Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>> scopeMap =
            this.tracesByScope.computeIfAbsent(
                span.getInstrumentationScopeInfo(), m -> new HashMap<>());

        Map<InternalAttributeKeyImpl<?>, AttributeType> spanKindMap =
            scopeMap.computeIfAbsent(span.getKind(), s -> new HashMap<>());

        for (Map.Entry<AttributeKey<?>, Object> key : span.getAttributes().asMap().entrySet()) {
          if (!(key.getKey() instanceof InternalAttributeKeyImpl)) {
            // We only collect internal attributes, so skip any non-internal attributes.
            continue;
          }
          InternalAttributeKeyImpl<?> keyImpl = (InternalAttributeKeyImpl<?>) key.getKey();
          if (!spanKindMap.containsKey(keyImpl)) {
            spanKindMap.put(keyImpl, key.getValue() != null ? key.getKey().getType() : null);
          }
        }

        InstrumentationScopeInfo scopeInfo = span.getInstrumentationScopeInfo();
        if (!scopeInfo.getName().equals("test")) {
          instrumentationScopes.add(scopeInfo);
        }
      }
    }
  }

  /**
   * Collects the events emitted so far, if telemetry metadata collection is enabled. Called after
   * every test, so that events are captured whether or not the test asserted on them.
   */
  public void collectEmittedEventsIfEnabled() {
    if (Boolean.getBoolean("collectMetadata")) {
      collectEmittedEvents(getExportedLogRecords());
    }
  }

  private void collectEmittedEvents(List<LogRecordData> logRecords) {
    for (LogRecordData logRecord : logRecords) {
      String eventName = eventName(logRecord);
      if (eventName == null) {
        // Not an event, just an ordinary log record (for example from a logging library bridge).
        continue;
      }

      Map<CollectedEvent.Key, CollectedEvent> scopeMap =
          this.eventsByScope.computeIfAbsent(
              logRecord.getInstrumentationScopeInfo(), s -> new HashMap<>());

      // The severity is part of the event's identity: the same event name can be emitted at more
      // than one severity by a single scope, and each of those is a distinct documented shape.
      Severity severity = logRecord.getSeverity();
      String severityName =
          (severity != null && severity != Severity.UNDEFINED_SEVERITY_NUMBER)
              ? severity.name()
              : null;
      CollectedEvent event =
          scopeMap.computeIfAbsent(
              new CollectedEvent.Key(eventName, severityName), e -> new CollectedEvent());

      for (AttributeKey<?> key : logRecord.getAttributes().asMap().keySet()) {
        if (!(key instanceof InternalAttributeKeyImpl)) {
          // We only collect internal attributes, so skip any non-internal attributes.
          continue;
        }
        if (EVENT_NAME_ATTRIBUTE_KEY.equals(key.getKey())) {
          // This attribute carries the event's identity, it is not one of its attributes.
          continue;
        }
        event.addAttributeKey((InternalAttributeKeyImpl<?>) key);
      }

      InstrumentationScopeInfo scopeInfo = logRecord.getInstrumentationScopeInfo();
      if (!scopeInfo.getName().equals("test")) {
        instrumentationScopes.add(scopeInfo);
      }
    }
  }

  /**
   * Returns the name of the event the given log record represents, or {@code null} if it is not an
   * event.
   *
   * <p>Instrumentation names events in one of two ways: newer code calls {@code
   * LogRecordBuilder.setEventName(String)}, while older code sets an {@code event.name} attribute.
   * Both are recognized here.
   */
  @Nullable
  private static String eventName(LogRecordData logRecord) {
    String eventName = logRecord.getEventName();
    if (eventName == null || eventName.isEmpty()) {
      eventName = logRecord.getAttributes().get(EVENT_NAME_ATTRIBUTE);
    }
    return eventName == null || eventName.isEmpty() ? null : eventName;
  }

  public List<LogRecordData> waitForLogRecords(int numberOfLogRecords) {
    awaitUntilAsserted(
        () -> assertThat(getExportedLogRecords().size()).isEqualTo(numberOfLogRecords),
        await().timeout(Duration.ofSeconds(20)));
    return getExportedLogRecords();
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertLogRecords(Consumer<LogRecordDataAssert>... assertions) {
    waitAndAssertLogRecords(asList(assertions));
  }

  public void waitAndAssertLogRecords(
      Iterable<? extends Consumer<LogRecordDataAssert>> assertions) {
    List<Consumer<LogRecordDataAssert>> assertionsList = new ArrayList<>();
    assertions.forEach(assertionsList::add);

    List<LogRecordData> logRecordDataList = waitForLogRecords(assertionsList.size());
    Iterator<Consumer<LogRecordDataAssert>> assertionIterator = assertionsList.iterator();
    for (LogRecordData logRecordData : logRecordDataList) {
      assertionIterator.next().accept(assertThat(logRecordData));
    }
  }

  private List<MetricData> instrumentationMetrics(String instrumentationName) {
    return getExportedMetrics().stream()
        .filter(m -> m.getInstrumentationScopeInfo().getName().equals(instrumentationName))
        .collect(toList());
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  public <E extends Exception> void runWithSpan(String spanName, ThrowingRunnable<E> callback)
      throws E {
    runWithSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  public <T, E extends Throwable> T runWithSpan(String spanName, ThrowingSupplier<T, E> callback)
      throws E {
    return getTestInstrumenters().runWithSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP CLIENT span with name {@code
   * spanName}.
   */
  public <E extends Throwable> void runWithHttpClientSpan(
      String spanName, ThrowingRunnable<E> callback) throws E {
    runWithHttpClientSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP CLIENT span with name {@code
   * spanName}.
   */
  public <T, E extends Throwable> T runWithHttpClientSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return getTestInstrumenters().runWithHttpClientSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP SERVER span with name {@code
   * spanName}.
   */
  public <E extends Throwable> void runWithHttpServerSpan(ThrowingRunnable<E> callback) throws E {
    runWithHttpServerSpan(
        () -> {
          callback.run();
          return null;
        });
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP SERVER span with name {@code
   * spanName}.
   */
  public <T, E extends Throwable> T runWithHttpServerSpan(ThrowingSupplier<T, E> callback)
      throws E {
    return getTestInstrumenters().runWithHttpServerSpan(callback);
  }

  /** Runs the provided {@code callback} inside the scope of a non-recording span. */
  public <T, E extends Throwable> T runWithNonRecordingSpan(ThrowingSupplier<T, E> callback)
      throws E {
    return getTestInstrumenters().runWithNonRecordingSpan(callback);
  }

  private TestInstrumenters getTestInstrumenters() {
    if (testInstrumenters == null) {
      testInstrumenters = new TestInstrumenters(openTelemetry);
    }
    return testInstrumenters;
  }

  private static void awaitUntilAsserted(Runnable runnable) {
    awaitUntilAsserted(runnable, await());
  }

  private static void awaitUntilAsserted(Runnable runnable, ConditionFactory conditionFactory) {
    try {
      conditionFactory.untilAsserted(runnable::run);
    } catch (Throwable t) {
      // awaitility is doing a jmx call that is not implemented in GraalVM:
      // call:
      // https://github.com/awaitility/awaitility/blob/fbe16add874b4260dd240108304d5c0be84eabc8/awaitility/src/main/java/org/awaitility/core/ConditionAwaiter.java#L157
      // see https://github.com/oracle/graal/issues/6101 (spring boot graal native image)
      if (t.getClass().getName().equals("com.oracle.svm.core.jdk.UnsupportedFeatureError")
          || t instanceof ConditionTimeoutException) {
        // Don't throw this failure since the stack is the awaitility thread, causing confusion.
        // Instead, just assert one more time on the test thread, which will fail with a better
        // stack trace - that is on the same thread as the test.
        // TODO: There is probably a better way to do this.
        runnable.run();
      } else {
        throw t;
      }
    }
  }
}
