/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.internal.InternalAttributeKeyImpl;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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

  private final TestInstrumenters testInstrumenters;
  protected Map<InstrumentationScopeInfo, Map<String, MetricData>> metricsByScope = new HashMap<>();

  /**
   * Stores traces by scope, where each scope contains a map of span kinds to a map of attribute
   * keys to their types. This is used to collect metadata about the spans emitted during tests.
   */
  protected Map<
          InstrumentationScopeInfo, Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>>>
      tracesByScope = new HashMap<>();

  protected InstrumentationTestRunner(OpenTelemetry openTelemetry) {
    testInstrumenters = new TestInstrumenters(openTelemetry);
  }

  public abstract void beforeTestClass();

  public abstract void afterTestClass() throws IOException;

  public abstract void clearAllExportedData();

  public abstract OpenTelemetry getOpenTelemetry();

  public abstract List<SpanData> getExportedSpans();

  public abstract List<MetricData> getExportedMetrics();

  public abstract List<LogRecordData> getExportedLogRecords();

  public abstract boolean forceFlushCalled();

  /** Return a list of all captured traces, where each trace is a sorted list of spans. */
  public final List<List<SpanData>> traces() {
    return TelemetryDataUtil.groupTraces(getExportedSpans());
  }

  public final List<List<SpanData>> waitForTraces(int numberOfTraces) {
    try {
      return TelemetryDataUtil.waitForTraces(
          this::getExportedSpans, numberOfTraces, 20, TimeUnit.SECONDS);
    } catch (TimeoutException | InterruptedException e) {
      throw new AssertionError("Error waiting for " + numberOfTraces + " traces", e);
    }
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertSortedTraces(
      Comparator<List<SpanData>> traceComparator, Consumer<TraceAssert>... assertions) {
    waitAndAssertTraces(traceComparator, Arrays.asList(assertions), true);
  }

  public final void waitAndAssertSortedTraces(
      Comparator<List<SpanData>> traceComparator,
      Iterable<? extends Consumer<TraceAssert>> assertions) {
    waitAndAssertTraces(traceComparator, assertions, true);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertTracesWithoutScopeVersionVerification(
      Consumer<TraceAssert>... assertions) {
    waitAndAssertTracesWithoutScopeVersionVerification(Arrays.asList(assertions));
  }

  public final <T extends Consumer<TraceAssert>>
      void waitAndAssertTracesWithoutScopeVersionVerification(Iterable<T> assertions) {
    waitAndAssertTraces(null, assertions, false);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertTraces(Consumer<TraceAssert>... assertions) {
    waitAndAssertTraces(Arrays.asList(assertions));
  }

  public final <T extends Consumer<TraceAssert>> void waitAndAssertTraces(Iterable<T> assertions) {
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

    if (Boolean.getBoolean("collectMetadata") && Boolean.getBoolean("collectSpans")) {
      collectEmittedSpans(traces);
    }
  }

  /**
   * Waits for the assertion applied to all metrics of the given instrumentation and metric name to
   * pass.
   */
  public final void waitAndAssertMetrics(
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

    if (Boolean.getBoolean("collectMetadata")) {
      collectEmittedMetrics(getExportedMetrics());
    }
  }

  private void collectEmittedMetrics(List<MetricData> metrics) {
    for (MetricData metric : metrics) {
      Map<String, MetricData> scopeMap =
          this.metricsByScope.computeIfAbsent(
              metric.getInstrumentationScopeInfo(), m -> new HashMap<>());

      if (!scopeMap.containsKey(metric.getName())) {
        scopeMap.put(metric.getName(), metric);
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
      }
    }
  }

  public final List<LogRecordData> waitForLogRecords(int numberOfLogRecords) {
    awaitUntilAsserted(
        () -> assertThat(getExportedLogRecords().size()).isEqualTo(numberOfLogRecords),
        await().timeout(Duration.ofSeconds(20)));
    return getExportedLogRecords();
  }

  private List<MetricData> instrumentationMetrics(String instrumentationName) {
    return getExportedMetrics().stream()
        .filter(m -> m.getInstrumentationScopeInfo().getName().equals(instrumentationName))
        .collect(Collectors.toList());
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  public final <E extends Exception> void runWithSpan(String spanName, ThrowingRunnable<E> callback)
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
  public final <T, E extends Throwable> T runWithSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return testInstrumenters.runWithSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP CLIENT span with name {@code
   * spanName}.
   */
  public final <E extends Throwable> void runWithHttpClientSpan(
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
  public final <T, E extends Throwable> T runWithHttpClientSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    return testInstrumenters.runWithHttpClientSpan(spanName, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an HTTP SERVER span with name {@code
   * spanName}.
   */
  public final <E extends Throwable> void runWithHttpServerSpan(ThrowingRunnable<E> callback)
      throws E {
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
  public final <T, E extends Throwable> T runWithHttpServerSpan(ThrowingSupplier<T, E> callback)
      throws E {
    return testInstrumenters.runWithHttpServerSpan(callback);
  }

  /** Runs the provided {@code callback} inside the scope of a non-recording span. */
  public final <T, E extends Throwable> T runWithNonRecordingSpan(ThrowingSupplier<T, E> callback)
      throws E {
    return testInstrumenters.runWithNonRecordingSpan(callback);
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
