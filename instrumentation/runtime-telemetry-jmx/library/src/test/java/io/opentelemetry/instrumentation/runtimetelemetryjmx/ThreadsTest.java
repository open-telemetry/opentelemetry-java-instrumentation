/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjmx;

import static io.opentelemetry.instrumentation.runtimetelemetryjmx.Threads.DAEMON;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThreadsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private ThreadMXBean threadBean;

  @Test
  void registerObservers() {
    when(threadBean.getThreadCount()).thenReturn(7);
    when(threadBean.getDaemonThreadCount()).thenReturn(2);

    Threads.INSTANCE.registerObservers(testing.getOpenTelemetry(), threadBean);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-jmx",
        "process.runtime.jvm.threads.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(ScopeUtil.EXPECTED_SCOPE)
                        .hasDescription("Number of executing threads")
                        .hasUnit("1")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(2)
                                                .hasAttributesSatisfying(equalTo(DAEMON, true)),
                                        point ->
                                            point
                                                .hasValue(5)
                                                .hasAttributesSatisfying(
                                                    equalTo(DAEMON, false))))));
  }
}
