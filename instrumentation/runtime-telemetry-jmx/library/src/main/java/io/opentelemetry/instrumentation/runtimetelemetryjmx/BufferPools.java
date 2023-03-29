/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjmx;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Registers measurements that generate metrics about buffer pools.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * BufferPools.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   process.runtime.jvm.buffer.usage.usage{pool="buffer_pool"} 500
 *   process.runtime.jvm.buffer.usage.max{pool="buffer_pool"} 1500
 *   process.runtime.jvm.buffer.usage.count{pool="buffer_pool"} 15
 * </pre>
 */
public final class BufferPools {
  private static final AttributeKey<String> POOL_KEY = AttributeKey.stringKey("pool");

  /** Register observers for java runtime buffer pool metrics. */
  public static void registerObservers(OpenTelemetry openTelemetry) {
    List<BufferPoolMXBean> bufferBeans =
        ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
    registerObservers(openTelemetry, bufferBeans);
  }

  // Visible for testing
  static void registerObservers(OpenTelemetry openTelemetry, List<BufferPoolMXBean> bufferBeans) {
    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);

    meter
        .upDownCounterBuilder("process.runtime.jvm.buffer.usage")
        .setDescription("Memory that the Java virtual machine is using for this buffer pool")
        .setUnit("By")
        .buildWithCallback(callback(bufferBeans, BufferPoolMXBean::getMemoryUsed));

    meter
        .upDownCounterBuilder("process.runtime.jvm.buffer.limit")
        .setDescription("Total capacity of the buffers in this pool")
        .setUnit("By")
        .buildWithCallback(callback(bufferBeans, BufferPoolMXBean::getTotalCapacity));

    meter
        .upDownCounterBuilder("process.runtime.jvm.buffer.count")
        .setDescription("The number of buffers in the pool")
        .setUnit("{buffers}")
        .buildWithCallback(callback(bufferBeans, BufferPoolMXBean::getCount));
  }

  // Visible for testing
  static Consumer<ObservableLongMeasurement> callback(
      List<BufferPoolMXBean> bufferPools, Function<BufferPoolMXBean, Long> extractor) {
    List<Attributes> attributeSets = new ArrayList<>(bufferPools.size());
    for (BufferPoolMXBean pool : bufferPools) {
      attributeSets.add(Attributes.builder().put(POOL_KEY, pool.getName()).build());
    }
    return measurement -> {
      for (int i = 0; i < bufferPools.size(); i++) {
        Attributes attributes = attributeSets.get(i);
        long value = extractor.apply(bufferPools.get(i));
        if (value != -1) {
          measurement.record(value, attributes);
        }
      }
    };
  }

  private BufferPools() {}
}
