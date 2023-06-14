/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsUtil;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers measurements that generate metrics about JVM classes.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Classes.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   process.runtime.jvm.classes.loaded 100
 *   process.runtime.jvm.classes.unloaded 2
 *   process.runtime.jvm.classes.current_loaded 98
 * </pre>
 */
public final class Classes {

  // Visible for testing
  static final Classes INSTANCE = new Classes();

  /** Register observers for java runtime class metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    return INSTANCE.registerObservers(openTelemetry, ManagementFactory.getClassLoadingMXBean());
  }

  // Visible for testing
  List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry, ClassLoadingMXBean classBean) {
    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);
    List<AutoCloseable> observables = new ArrayList<>();
    observables.add(
        meter
            .counterBuilder("process.runtime.jvm.classes.loaded")
            .setDescription("Number of classes loaded since JVM start")
            .setUnit("{class}")
            .buildWithCallback(
                observableMeasurement ->
                    observableMeasurement.record(classBean.getTotalLoadedClassCount())));

    observables.add(
        meter
            .counterBuilder("process.runtime.jvm.classes.unloaded")
            .setDescription("Number of classes unloaded since JVM start")
            .setUnit("{class}")
            .buildWithCallback(
                observableMeasurement ->
                    observableMeasurement.record(classBean.getUnloadedClassCount())));
    observables.add(
        meter
            .upDownCounterBuilder("process.runtime.jvm.classes.current_loaded")
            .setDescription("Number of classes currently loaded")
            .setUnit("{class}")
            .buildWithCallback(
                observableMeasurement ->
                    observableMeasurement.record(classBean.getLoadedClassCount())));
    return observables;
  }

  private Classes() {}
}
