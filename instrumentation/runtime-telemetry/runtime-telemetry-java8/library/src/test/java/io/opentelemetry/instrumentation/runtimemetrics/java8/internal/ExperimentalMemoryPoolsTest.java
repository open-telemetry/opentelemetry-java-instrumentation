/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.runtimemetrics.java8.ScopeUtil.EXPECTED_SCOPE;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExperimentalMemoryPoolsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private MemoryPoolMXBean heapPoolBean;
  @Mock private MemoryPoolMXBean nonHeapPoolBean;

  @Mock private MemoryUsage heapPoolUsage;
  @Mock private MemoryUsage nonHeapUsage;

  private List<MemoryPoolMXBean> beans;

  @BeforeEach
  void setup() {
    when(heapPoolBean.getName()).thenReturn("heap_pool");
    when(heapPoolBean.getType()).thenReturn(MemoryType.HEAP);
    when(heapPoolBean.getUsage()).thenReturn(heapPoolUsage);
    when(nonHeapPoolBean.getName()).thenReturn("non_heap_pool");
    when(nonHeapPoolBean.getType()).thenReturn(MemoryType.NON_HEAP);
    when(nonHeapPoolBean.getUsage()).thenReturn(nonHeapUsage);
    beans = Arrays.asList(heapPoolBean, nonHeapPoolBean);
  }

  @Test
  void registerObservers() {
    when(heapPoolUsage.getInit()).thenReturn(11L);
    when(nonHeapUsage.getInit()).thenReturn(15L);
    ExperimentalMemoryPools.registerObservers(testing.getOpenTelemetry(), beans);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.memory.init",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("Measure of initial memory requested.")
                        .hasUnit("By")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(11)
                                            .hasAttribute(
                                                stringKey("jvm.memory.pool.name"), "heap_pool")
                                            .hasAttribute(stringKey("jvm.memory.type"), "heap"),
                                    point ->
                                        point
                                            .hasValue(15)
                                            .hasAttribute(
                                                stringKey("jvm.memory.pool.name"), "non_heap_pool")
                                            .hasAttribute(
                                                stringKey("jvm.memory.type"), "non_heap")))));
  }
}
