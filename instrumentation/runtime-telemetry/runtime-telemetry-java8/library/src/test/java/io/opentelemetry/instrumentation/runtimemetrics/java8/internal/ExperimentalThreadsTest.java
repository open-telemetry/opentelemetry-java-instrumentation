/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import static io.opentelemetry.instrumentation.runtimemetrics.java8.ScopeUtil.EXPECTED_SCOPE;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("deprecation") // until ExperimentalThreads is renamed
@ExtendWith(MockitoExtension.class)
class ExperimentalThreadsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private ThreadMXBean threadBean;

  @Test
  void registerObservers_DeadlockedThreads() {
    when(threadBean.findDeadlockedThreads()).thenReturn(new long[] {1, 2, 3});
    when(threadBean.findMonitorDeadlockedThreads()).thenReturn(new long[] {4, 5});

    ExperimentalThreads.registerObservers(testing.getOpenTelemetry(), threadBean);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.thread.deadlock.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription(
                            "Number of platform threads that are in deadlock waiting to acquire object monitors or ownable synchronizers.")
                        .hasUnit("{thread}")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(point -> point.hasValue(3)))));

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.thread.monitor_deadlock.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription(
                            "Number of platform threads that are in deadlock waiting to acquire object monitors.")
                        .hasUnit("{thread}")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(point -> point.hasValue(2)))));
  }

  @Test
  void registerObservers_NoDeadlockedThreads() {
    when(threadBean.findDeadlockedThreads()).thenReturn(null);
    when(threadBean.findMonitorDeadlockedThreads()).thenReturn(null);

    ExperimentalThreads.registerObservers(testing.getOpenTelemetry(), threadBean);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.thread.deadlock.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(point -> point.hasValue(0)))));

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.thread.monitor_deadlock.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(point -> point.hasValue(0)))));
  }
}
