/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers measurements that generate metrics about JVM memory areas.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * MemoryPools.registerObservers();
 * }</pre>
 *
 * <p>Example metrics being exported: Component
 *
 * <pre>
 *   runtime.jvm.memory.area{type="used",area="heap"} 2000000
 *   runtime.jvm.memory.area{type="committed",area="non_heap"} 200000
 *   runtime.jvm.memory.area{type="used",pool="PS Eden Space"} 2000
 * </pre>
 */
public final class MemoryPools {
  // Visible for testing
  static final AttributeKey<String> TYPE_KEY = AttributeKey.stringKey("type");
  // Visible for testing
  static final AttributeKey<String> AREA_KEY = AttributeKey.stringKey("area");
  private static final AttributeKey<String> POOL_KEY = AttributeKey.stringKey("pool");

  private static final String USED = "used";
  private static final String COMMITTED = "committed";
  private static final String MAX = "max";
  private static final String HEAP = "heap";
  private static final String NON_HEAP = "non_heap";

  private static final Attributes COMMITTED_HEAP =
      Attributes.of(TYPE_KEY, COMMITTED, AREA_KEY, HEAP);
  private static final Attributes USED_HEAP = Attributes.of(TYPE_KEY, USED, AREA_KEY, HEAP);
  private static final Attributes MAX_HEAP = Attributes.of(TYPE_KEY, MAX, AREA_KEY, HEAP);

  private static final Attributes COMMITTED_NON_HEAP =
      Attributes.of(TYPE_KEY, COMMITTED, AREA_KEY, NON_HEAP);
  private static final Attributes USED_NON_HEAP = Attributes.of(TYPE_KEY, USED, AREA_KEY, NON_HEAP);
  private static final Attributes MAX_NON_HEAP = Attributes.of(TYPE_KEY, MAX, AREA_KEY, NON_HEAP);

  /** Register only the "area" measurements. */
  public static void registerMemoryAreaObservers() {
    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    Meter meter = GlobalMeterProvider.get().get(MemoryPools.class.getName());
    meter
        .gaugeBuilder("runtime.jvm.memory.area")
        .ofLongs()
        .setDescription("Bytes of a given JVM memory area.")
        .setUnit("By")
        .buildWithCallback(
            resultLongObserver -> {
              observeHeap(resultLongObserver, memoryBean.getHeapMemoryUsage());
              observeNonHeap(resultLongObserver, memoryBean.getNonHeapMemoryUsage());
            });
  }

  /** Register only the "pool" measurements. */
  public static void registerMemoryPoolObservers() {
    List<MemoryPoolMXBean> poolBeans = ManagementFactory.getMemoryPoolMXBeans();
    Meter meter = GlobalMeterProvider.get().get(MemoryPools.class.getName());
    List<Attributes> usedLabelSets = new ArrayList<>(poolBeans.size());
    List<Attributes> committedLabelSets = new ArrayList<>(poolBeans.size());
    List<Attributes> maxLabelSets = new ArrayList<>(poolBeans.size());
    for (MemoryPoolMXBean pool : poolBeans) {
      usedLabelSets.add(Attributes.of(TYPE_KEY, USED, POOL_KEY, pool.getName()));
      committedLabelSets.add(Attributes.of(TYPE_KEY, COMMITTED, POOL_KEY, pool.getName()));
      maxLabelSets.add(Attributes.of(TYPE_KEY, MAX, POOL_KEY, pool.getName()));
    }
    meter
        .gaugeBuilder("runtime.jvm.memory.pool")
        .ofLongs()
        .setDescription("Bytes of a given JVM memory pool.")
        .setUnit("By")
        .buildWithCallback(
            resultLongObserver -> {
              for (int i = 0; i < poolBeans.size(); i++) {
                MemoryUsage poolUsage = poolBeans.get(i).getUsage();
                if (poolUsage != null) {
                  observe(
                      resultLongObserver,
                      poolUsage,
                      usedLabelSets.get(i),
                      committedLabelSets.get(i),
                      maxLabelSets.get(i));
                }
              }
            });
  }

  /** Register all measurements provided by this module. */
  public static void registerObservers() {
    registerMemoryAreaObservers();
    registerMemoryPoolObservers();
  }

  static void observeHeap(ObservableLongMeasurement measurement, MemoryUsage usage) {
    observe(measurement, usage, USED_HEAP, COMMITTED_HEAP, MAX_HEAP);
  }

  static void observeNonHeap(ObservableLongMeasurement measurement, MemoryUsage usage) {
    observe(measurement, usage, USED_NON_HEAP, COMMITTED_NON_HEAP, MAX_NON_HEAP);
  }

  private static void observe(
      ObservableLongMeasurement measurement,
      MemoryUsage usage,
      Attributes usedAttributes,
      Attributes committedAttributes,
      Attributes maxAttributes) {
    // TODO: Decide if init is needed or not. It is a constant that can be queried once on startup.
    // if (usage.getInit() != -1) {
    //  measurement.observe(usage.getInit(), ...);
    // }
    measurement.observe(usage.getUsed(), usedAttributes);
    measurement.observe(usage.getCommitted(), committedAttributes);
    // TODO: Decide if max is needed or not. It is a constant that can be queried once on startup.
    if (usage.getMax() != -1) {
      measurement.observe(usage.getMax(), maxAttributes);
    }
  }

  private MemoryPools() {}
}
