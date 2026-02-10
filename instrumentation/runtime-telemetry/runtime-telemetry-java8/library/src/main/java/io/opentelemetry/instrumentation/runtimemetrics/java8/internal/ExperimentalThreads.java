/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers measurements that generate experimental metrics about JVM threads.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 *
 * @deprecated Use {@link io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetrics}
 *     instead, and configure metric views to select specific metrics.
 */
@Deprecated
public final class ExperimentalThreads {

  /** Register observers for java runtime experimental thread metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    return registerObservers(openTelemetry, ManagementFactory.getThreadMXBean());
  }

  // Visible for testing
  static List<AutoCloseable> registerObservers(
      OpenTelemetry openTelemetry, ThreadMXBean threadBean) {
    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);
    List<AutoCloseable> observables = new ArrayList<>();

    observables.add(
        meter
            .upDownCounterBuilder("jvm.thread.deadlock.count")
            .setDescription(
                "Number of platform threads that are in deadlock waiting to acquire object monitors or ownable synchronizers.")
            .setUnit("{thread}")
            .buildWithCallback(
                measurement ->
                    measurement.record(
                        nullSafeArrayLength(threadBean.findDeadlockedThreads()))));

    observables.add(
        meter
            .upDownCounterBuilder("jvm.thread.monitor_deadlock.count")
            .setDescription(
                "Number of platform threads that are in deadlock waiting to acquire object monitors.")
            .setUnit("{thread}")
            .buildWithCallback(
                measurement ->
                    measurement.record(
                        nullSafeArrayLength(threadBean.findMonitorDeadlockedThreads()))));

    return observables;
  }

  private static long nullSafeArrayLength(long[] array) {
    return array == null ? 0 : array.length;
  }

  private ExperimentalThreads() {}
}
