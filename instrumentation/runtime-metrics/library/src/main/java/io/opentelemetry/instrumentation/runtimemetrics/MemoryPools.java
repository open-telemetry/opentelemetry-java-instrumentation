/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import io.opentelemetry.api.metrics.AsynchronousInstrument.LongResult;
import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.api.metrics.LongUpDownSumObserver;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers observers that generate metrics about JVM memory areas.
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
 *   runtime.jvm.memory.area{type="committed",area="nonheap"} 200000
 *   runtime.jvm.memory.area{type="used",pool="PS Eden Space"} 2000
 * </pre>
 */
public final class MemoryPools {
  private static final String TYPE_LABEL_KEY = "type";
  private static final String AREA_LABEL_KEY = "area";
  private static final String POOL_LABEL_KEY = "pool";
  private static final String USED = "used";
  private static final String COMMITTED = "committed";
  private static final String MAX = "max";
  private static final String HEAP = "heap";
  private static final String NON_HEAP = "non_heap";

  private static final Labels COMMITTED_HEAP =
      Labels.of(TYPE_LABEL_KEY, COMMITTED, AREA_LABEL_KEY, HEAP);
  private static final Labels USED_HEAP = Labels.of(TYPE_LABEL_KEY, USED, AREA_LABEL_KEY, HEAP);
  private static final Labels MAX_HEAP = Labels.of(TYPE_LABEL_KEY, MAX, AREA_LABEL_KEY, HEAP);

  private static final Labels COMMITTED_NON_HEAP =
      Labels.of(TYPE_LABEL_KEY, COMMITTED, AREA_LABEL_KEY, NON_HEAP);
  private static final Labels USED_NON_HEAP =
      Labels.of(TYPE_LABEL_KEY, USED, AREA_LABEL_KEY, NON_HEAP);
  private static final Labels MAX_NON_HEAP =
      Labels.of(TYPE_LABEL_KEY, MAX, AREA_LABEL_KEY, NON_HEAP);

  /** Register only the "area" observers. */
  public static void registerMemoryAreaObservers() {
    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    Meter meter = GlobalMetricsProvider.getMeter(MemoryPools.class.getName());
    final LongUpDownSumObserver areaMetric =
        meter
            .longUpDownSumObserverBuilder("runtime.jvm.memory.area")
            .setDescription("Bytes of a given JVM memory area.")
            .setUnit("By")
            .setUpdater(
                resultLongObserver -> {
                  observeHeap(resultLongObserver, memoryBean.getHeapMemoryUsage());
                  observeNonHeap(resultLongObserver, memoryBean.getNonHeapMemoryUsage());
                })
            .build();
  }

  /** Register only the "pool" observers. */
  public static void registerMemoryPoolObservers() {
    List<MemoryPoolMXBean> poolBeans = ManagementFactory.getMemoryPoolMXBeans();
    Meter meter = GlobalMetricsProvider.getMeter(MemoryPools.class.getName());
    List<Labels> usedLabelSets = new ArrayList<>(poolBeans.size());
    List<Labels> committedLabelSets = new ArrayList<>(poolBeans.size());
    List<Labels> maxLabelSets = new ArrayList<>(poolBeans.size());
    for (MemoryPoolMXBean pool : poolBeans) {
      usedLabelSets.add(Labels.of(TYPE_LABEL_KEY, USED, POOL_LABEL_KEY, pool.getName()));
      committedLabelSets.add(Labels.of(TYPE_LABEL_KEY, COMMITTED, POOL_LABEL_KEY, pool.getName()));
      maxLabelSets.add(Labels.of(TYPE_LABEL_KEY, MAX, POOL_LABEL_KEY, pool.getName()));
    }
    meter
        .longUpDownSumObserverBuilder("runtime.jvm.memory.pool")
        .setDescription("Bytes of a given JVM memory pool.")
        .setUnit("By")
        .setUpdater(
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
            })
        .build();
  }

  /** Register all observers provided by this module. */
  public static void registerObservers() {
    registerMemoryAreaObservers();
    registerMemoryPoolObservers();
  }

  static void observeHeap(LongResult observer, MemoryUsage usage) {
    observe(observer, usage, USED_HEAP, COMMITTED_HEAP, MAX_HEAP);
  }

  static void observeNonHeap(LongResult observer, MemoryUsage usage) {
    observe(observer, usage, USED_NON_HEAP, COMMITTED_NON_HEAP, MAX_NON_HEAP);
  }

  private static void observe(
      LongResult observer,
      MemoryUsage usage,
      Labels usedLabels,
      Labels committedLabels,
      Labels maxLabels) {
    // TODO: Decide if init is needed or not. It is a constant that can be queried once on startup.
    // if (usage.getInit() != -1) {
    //  observer.observe(usage.getInit(), ...);
    // }
    observer.observe(usage.getUsed(), usedLabels);
    observer.observe(usage.getCommitted(), committedLabels);
    // TODO: Decide if max is needed or not. It is a constant that can be queried once on startup.
    if (usage.getMax() != -1) {
      observer.observe(usage.getMax(), maxLabels);
    }
  }

  private MemoryPools() {}
}
