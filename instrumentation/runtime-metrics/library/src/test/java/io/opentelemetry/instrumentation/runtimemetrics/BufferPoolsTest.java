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
import java.lang.management.BufferPoolMXBean;
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
public class BufferPoolsTest {
  @Spy private ObservableLongMeasurement measurement;
  @Mock private BufferPoolMXBean bufferPoolBean;
  private List<BufferPoolMXBean> beans;

  @BeforeEach
  void setup() {
    when(bufferPoolBean.getName()).thenReturn("buffer_pool_1");
    beans = Arrays.asList(bufferPoolBean);
  }

  @Test
  void callback_Records() {
    when(bufferPoolBean.getMemoryUsed()).thenReturn(1L);
    Consumer<ObservableLongMeasurement> callback =
        BufferPools.callback(beans, BufferPoolMXBean::getMemoryUsed);
    callback.accept(measurement);
    verify(measurement).record(1, Attributes.builder().put("pool", "buffer_pool_1").build());
  }

  @Test
  void callback_SkipRecord() {
    when(bufferPoolBean.getMemoryUsed()).thenReturn(-1L);
    Consumer<ObservableLongMeasurement> callback =
        BufferPools.callback(beans, BufferPoolMXBean::getMemoryUsed);
    callback.accept(measurement);
    verify(measurement, never()).record(eq(-1), any());
  }
}
