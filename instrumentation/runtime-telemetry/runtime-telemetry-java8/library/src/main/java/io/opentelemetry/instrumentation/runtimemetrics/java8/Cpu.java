/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.CpuMethods;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsUtil;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Registers measurements that generate metrics about CPU.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Cpu.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   process.runtime.jvm.system.cpu.load_1m 2.2
 *   process.runtime.jvm.system.cpu.utilization 0.15
 *   process.runtime.jvm.cpu.utilization 0.1
 * </pre>
 *
 * <p>In case you enable the preview of stable JVM semantic conventions (e.g. by setting the {@code
 * otel.semconv-stability.opt-in} system property to {@code jvm}), the metrics being exported will
 * follow <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md">the
 * most recent JVM semantic conventions</a>. This is how the example above looks when stable JVM
 * semconv is enabled:
 *
 * <pre>
 *   jvm.cpu.time 20.42
 *   jvm.cpu.count 8
 *   jvm.cpu.recent_utilization 0.1
 * </pre>
 */
public final class Cpu {

  // Visible for testing
  static final Cpu INSTANCE = new Cpu();

  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  /** Register observers for java runtime CPU metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    return INSTANCE.registerObservers(
        openTelemetry,
        ManagementFactory.getOperatingSystemMXBean(),
        Runtime.getRuntime()::availableProcessors,
        CpuMethods.processCpuTime(),
        CpuMethods.systemCpuUtilization(),
        CpuMethods.processCpuUtilization());
  }

  // Visible for testing
  List<AutoCloseable> registerObservers(
      OpenTelemetry openTelemetry,
      OperatingSystemMXBean osBean,
      IntSupplier availableProcessors,
      @Nullable Supplier<Long> processCpuTime,
      @Nullable Supplier<Double> systemCpuUtilization,
      @Nullable Supplier<Double> processCpuUtilization) {
    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);
    List<AutoCloseable> observables = new ArrayList<>();

    if (SemconvStability.emitOldJvmSemconv()) {
      observables.add(
          meter
              .gaugeBuilder("process.runtime.jvm.system.cpu.load_1m")
              .setDescription("Average CPU load of the whole system for the last minute")
              .setUnit("{run_queue_item}")
              .buildWithCallback(
                  observableMeasurement -> {
                    double loadAverage = osBean.getSystemLoadAverage();
                    if (loadAverage >= 0) {
                      observableMeasurement.record(loadAverage);
                    }
                  }));
      if (systemCpuUtilization != null) {
        observables.add(
            meter
                .gaugeBuilder("process.runtime.jvm.system.cpu.utilization")
                .setDescription("Recent cpu utilization for the whole system")
                .setUnit("1")
                .buildWithCallback(
                    observableMeasurement -> {
                      Double cpuUsage = systemCpuUtilization.get();
                      if (cpuUsage != null && cpuUsage >= 0) {
                        observableMeasurement.record(cpuUsage);
                      }
                    }));
      }
      if (processCpuUtilization != null) {
        observables.add(
            meter
                .gaugeBuilder("process.runtime.jvm.cpu.utilization")
                .setDescription("Recent cpu utilization for the process")
                .setUnit("1")
                .buildWithCallback(
                    observableMeasurement -> {
                      Double cpuUsage = processCpuUtilization.get();
                      if (cpuUsage != null && cpuUsage >= 0) {
                        observableMeasurement.record(cpuUsage);
                      }
                    }));
      }
    }
    if (SemconvStability.emitStableJvmSemconv()) {
      if (processCpuTime != null) {
        observables.add(
            meter
                .counterBuilder("jvm.cpu.time")
                .ofDoubles()
                .setDescription("CPU time used by the process as reported by the JVM.")
                .setUnit("s")
                .buildWithCallback(
                    observableMeasurement -> {
                      Long cpuTimeNanos = processCpuTime.get();
                      if (cpuTimeNanos != null && cpuTimeNanos >= 0) {
                        observableMeasurement.record(cpuTimeNanos / NANOS_PER_S);
                      }
                    }));
      }
      observables.add(
          meter
              .upDownCounterBuilder("jvm.cpu.count")
              .setDescription("Number of processors available to the Java virtual machine.")
              .setUnit("{cpu}")
              .buildWithCallback(
                  observableMeasurement ->
                      observableMeasurement.record(availableProcessors.getAsInt())));
      if (processCpuUtilization != null) {
        observables.add(
            meter
                .gaugeBuilder("jvm.cpu.recent_utilization")
                .setDescription("Recent CPU utilization for the process as reported by the JVM.")
                .setUnit("1")
                .buildWithCallback(
                    observableMeasurement -> {
                      Double cpuUsage = processCpuUtilization.get();
                      if (cpuUsage != null && cpuUsage >= 0) {
                        observableMeasurement.record(cpuUsage);
                      }
                    }));
      }
    }
    return observables;
  }

  private Cpu() {}
}
