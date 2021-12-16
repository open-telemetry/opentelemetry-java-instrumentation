/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.lang.management.MemoryUsage;
import org.junit.jupiter.api.Test;

class MemoryPoolsTest {

  @Test
  void observeHeap() {
    ObservableLongMeasurement measurement = mock(ObservableLongMeasurement.class);
    MemoryPools.recordHeap(measurement, new MemoryUsage(-1, 1, 2, 3));
    verify(measurement)
        .record(1, Attributes.of(MemoryPools.TYPE_KEY, "used", MemoryPools.AREA_KEY, "heap"));
    verify(measurement)
        .record(2, Attributes.of(MemoryPools.TYPE_KEY, "committed", MemoryPools.AREA_KEY, "heap"));
    verify(measurement)
        .record(3, Attributes.of(MemoryPools.TYPE_KEY, "max", MemoryPools.AREA_KEY, "heap"));
    verifyNoMoreInteractions(measurement);
  }

  @Test
  void observeHeapNoMax() {
    ObservableLongMeasurement measurement = mock(ObservableLongMeasurement.class);
    MemoryPools.recordHeap(measurement, new MemoryUsage(-1, 1, 2, -1));
    verify(measurement)
        .record(1, Attributes.of(MemoryPools.TYPE_KEY, "used", MemoryPools.AREA_KEY, "heap"));
    verify(measurement)
        .record(2, Attributes.of(MemoryPools.TYPE_KEY, "committed", MemoryPools.AREA_KEY, "heap"));
    verifyNoMoreInteractions(measurement);
  }

  @Test
  void observeNonHeap() {
    ObservableLongMeasurement measurement = mock(ObservableLongMeasurement.class);
    MemoryPools.recordNonHeap(measurement, new MemoryUsage(-1, 4, 5, 6));
    verify(measurement)
        .record(4, Attributes.of(MemoryPools.TYPE_KEY, "used", MemoryPools.AREA_KEY, "non_heap"));
    verify(measurement)
        .record(
            5, Attributes.of(MemoryPools.TYPE_KEY, "committed", MemoryPools.AREA_KEY, "non_heap"));
    verify(measurement)
        .record(6, Attributes.of(MemoryPools.TYPE_KEY, "max", MemoryPools.AREA_KEY, "non_heap"));
    verifyNoMoreInteractions(measurement);
  }

  @Test
  void observeNonHeapNoMax() {
    ObservableLongMeasurement measurement = mock(ObservableLongMeasurement.class);
    MemoryPools.recordNonHeap(measurement, new MemoryUsage(-1, 4, 5, -1));
    verify(measurement)
        .record(4, Attributes.of(MemoryPools.TYPE_KEY, "used", MemoryPools.AREA_KEY, "non_heap"));
    verify(measurement)
        .record(
            5, Attributes.of(MemoryPools.TYPE_KEY, "committed", MemoryPools.AREA_KEY, "non_heap"));
    verifyNoMoreInteractions(measurement);
  }
}
