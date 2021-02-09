/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.opentelemetry.api.metrics.AsynchronousInstrument;
import io.opentelemetry.api.metrics.common.Labels;
import java.lang.management.MemoryUsage;
import org.junit.jupiter.api.Test;

class MemoryPoolsTest {

  @Test
  void observeHeap() {
    AsynchronousInstrument.LongResult observer = mock(AsynchronousInstrument.LongResult.class);
    MemoryPools.observeHeap(observer, new MemoryUsage(-1, 1, 2, 3));
    verify(observer).observe(1, Labels.of("type", "used", "area", "heap"));
    verify(observer).observe(2, Labels.of("type", "committed", "area", "heap"));
    verify(observer).observe(3, Labels.of("type", "max", "area", "heap"));
    verifyNoMoreInteractions(observer);
  }

  @Test
  void observeHeapNoMax() {
    AsynchronousInstrument.LongResult observer = mock(AsynchronousInstrument.LongResult.class);
    MemoryPools.observeHeap(observer, new MemoryUsage(-1, 1, 2, -1));
    verify(observer).observe(1, Labels.of("type", "used", "area", "heap"));
    verify(observer).observe(2, Labels.of("type", "committed", "area", "heap"));
    verifyNoMoreInteractions(observer);
  }

  @Test
  void observeNonHeap() {
    AsynchronousInstrument.LongResult observer = mock(AsynchronousInstrument.LongResult.class);
    MemoryPools.observeNonHeap(observer, new MemoryUsage(-1, 4, 5, 6));
    verify(observer).observe(4, Labels.of("type", "used", "area", "non_heap"));
    verify(observer).observe(5, Labels.of("type", "committed", "area", "non_heap"));
    verify(observer).observe(6, Labels.of("type", "max", "area", "non_heap"));
    verifyNoMoreInteractions(observer);
  }

  @Test
  void observeNonHeapNoMax() {
    AsynchronousInstrument.LongResult observer = mock(AsynchronousInstrument.LongResult.class);
    MemoryPools.observeNonHeap(observer, new MemoryUsage(-1, 4, 5, -1));
    verify(observer).observe(4, Labels.of("type", "used", "area", "non_heap"));
    verify(observer).observe(5, Labels.of("type", "committed", "area", "non_heap"));
    verifyNoMoreInteractions(observer);
  }
}
