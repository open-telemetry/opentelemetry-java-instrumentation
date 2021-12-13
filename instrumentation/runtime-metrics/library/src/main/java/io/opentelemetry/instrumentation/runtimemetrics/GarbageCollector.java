/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers observers that generate metrics about JVM garbage collectors.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * GarbageCollector.registerObservers();
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   runtime.jvm.gc.time{gc="PS1"} 6.7
 *   runtime.jvm.gc.count{gc="PS1"} 1
 * </pre>
 */
public final class GarbageCollector {
  private static final AttributeKey<String> GC_KEY = AttributeKey.stringKey("gc");

  /** Register all observers provided by this module. */
  public static void registerObservers() {
    // TODO(anuraaga): registerObservers should accept an OpenTelemetry instance
    List<GarbageCollectorMXBean> garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans();
    Meter meter =
        GlobalOpenTelemetry.get().getMeterProvider().get(GarbageCollector.class.getName());
    List<Attributes> labelSets = new ArrayList<>(garbageCollectors.size());
    for (GarbageCollectorMXBean gc : garbageCollectors) {
      labelSets.add(Attributes.of(GC_KEY, gc.getName()));
    }
    meter
        .counterBuilder("runtime.jvm.gc.time")
        .setDescription("Time spent in a given JVM garbage collector in milliseconds.")
        .setUnit("ms")
        .buildWithCallback(
            resultLongObserver -> {
              for (int i = 0; i < garbageCollectors.size(); i++) {
                resultLongObserver.record(
                    garbageCollectors.get(i).getCollectionTime(), labelSets.get(i));
              }
            });
    meter
        .counterBuilder("runtime.jvm.gc.count")
        .setDescription(
            "The number of collections that have occurred for a given JVM garbage collector.")
        .setUnit("collections")
        .buildWithCallback(
            resultLongObserver -> {
              for (int i = 0; i < garbageCollectors.size(); i++) {
                resultLongObserver.record(
                    garbageCollectors.get(i).getCollectionCount(), labelSets.get(i));
              }
            });
  }

  private GarbageCollector() {}
}
