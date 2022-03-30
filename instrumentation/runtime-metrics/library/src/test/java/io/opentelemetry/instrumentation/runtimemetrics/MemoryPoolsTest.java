/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemoryPoolsTest {

  @Spy private ObservableLongMeasurement measurement;

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
  void callback_Records() {
    when(heapPoolUsage.getUsed()).thenReturn(1L);
    when(nonHeapUsage.getUsed()).thenReturn(2L);

    Consumer<ObservableLongMeasurement> callback =
        MemoryPools.callback(beans, MemoryUsage::getUsed);
    callback.accept(measurement);

    verify(measurement)
        .record(1, Attributes.builder().put("pool", "heap_pool").put("type", "heap").build());
    verify(measurement)
        .record(
            2, Attributes.builder().put("pool", "non_heap_pool").put("type", "non_heap").build());
  }

  @Test
  void callback_SkipRecord() {
    when(heapPoolUsage.getMax()).thenReturn(1L);
    when(nonHeapUsage.getMax()).thenReturn(-1L);

    Consumer<ObservableLongMeasurement> callback = MemoryPools.callback(beans, MemoryUsage::getMax);
    callback.accept(measurement);

    verify(measurement)
        .record(1, Attributes.builder().put("pool", "heap_pool").put("type", "heap").build());
    verify(measurement, never()).record(eq(-1), any());
  }
}
