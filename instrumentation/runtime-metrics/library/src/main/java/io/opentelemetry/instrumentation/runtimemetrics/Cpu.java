/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
 */
public final class Cpu {

  // Visible for testing
  static final Cpu INSTANCE = new Cpu();

  private static final String OS_BEAN_J9 = "com.ibm.lang.management.OperatingSystemMXBean";
  private static final String OS_BEAN_HOTSPOT = "com.sun.management.OperatingSystemMXBean";
  private static final String METHOD_PROCESS_CPU_LOAD = "getProcessCpuLoad";
  private static final String METHOD_CPU_LOAD = "getCpuLoad";
  private static final String METHOD_SYSTEM_CPU_LOAD = "getSystemCpuLoad";

  @Nullable private static final Supplier<Double> processCpu;
  @Nullable private static final Supplier<Double> systemCpu;

  static {
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    Supplier<Double> processCpuSupplier =
        methodInvoker(osBean, OS_BEAN_J9, METHOD_PROCESS_CPU_LOAD);
    if (processCpuSupplier == null) {
      processCpuSupplier = methodInvoker(osBean, OS_BEAN_HOTSPOT, METHOD_PROCESS_CPU_LOAD);
    }
    processCpu = processCpuSupplier;

    Supplier<Double> systemCpuSupplier = methodInvoker(osBean, OS_BEAN_J9, METHOD_SYSTEM_CPU_LOAD);
    if (systemCpuSupplier == null) {
      systemCpuSupplier = methodInvoker(osBean, OS_BEAN_HOTSPOT, METHOD_CPU_LOAD);
    }
    if (systemCpuSupplier == null) {
      systemCpuSupplier = methodInvoker(osBean, OS_BEAN_HOTSPOT, METHOD_SYSTEM_CPU_LOAD);
    }
    systemCpu = systemCpuSupplier;
  }

  /** Register observers for java runtime class metrics. */
  public static void registerObservers(OpenTelemetry openTelemetry) {
    INSTANCE.registerObservers(
        openTelemetry, ManagementFactory.getOperatingSystemMXBean(), systemCpu, processCpu);
  }

  // Visible for testing
  void registerObservers(
      OpenTelemetry openTelemetry,
      OperatingSystemMXBean osBean,
      @Nullable Supplier<Double> systemCpuUsage,
      @Nullable Supplier<Double> processCpuUsage) {
    Meter meter = openTelemetry.getMeter("io.opentelemetry.runtime-metrics");

    meter
        .gaugeBuilder("process.runtime.jvm.system.cpu.load_1m")
        .setDescription("Average CPU load of the whole system for the last minute")
        .setUnit("1")
        .buildWithCallback(
            observableMeasurement -> {
              double loadAverage = osBean.getSystemLoadAverage();
              if (loadAverage >= 0) {
                observableMeasurement.record(loadAverage);
              }
            });

    if (systemCpuUsage != null) {
      meter
          .gaugeBuilder("process.runtime.jvm.system.cpu.utilization")
          .setDescription("Recent cpu utilization for the whole system")
          .setUnit("1")
          .buildWithCallback(
              observableMeasurement -> {
                Double cpuUsage = systemCpuUsage.get();
                if (cpuUsage != null && cpuUsage >= 0) {
                  observableMeasurement.record(cpuUsage);
                }
              });
    }

    if (processCpuUsage != null) {
      meter
          .gaugeBuilder("process.runtime.jvm.cpu.utilization")
          .setDescription("Recent cpu utilization for the process")
          .setUnit("1")
          .buildWithCallback(
              observableMeasurement -> {
                Double cpuUsage = processCpuUsage.get();
                if (cpuUsage != null && cpuUsage >= 0) {
                  observableMeasurement.record(cpuUsage);
                }
              });
    }
  }

  @Nullable
  @SuppressWarnings("ReturnValueIgnored")
  private static Supplier<Double> methodInvoker(
      OperatingSystemMXBean osBean, String osBeanClassName, String methodName) {
    try {
      Class<?> osBeanClass = Class.forName(osBeanClassName);
      osBeanClass.cast(osBean);
      Method method = osBeanClass.getDeclaredMethod(methodName);
      return () -> {
        try {
          return (double) method.invoke(osBean);
        } catch (IllegalAccessException | InvocationTargetException e) {
          return null;
        }
      };
    } catch (ClassNotFoundException | ClassCastException | NoSuchMethodException e) {
      return null;
    }
  }

  private Cpu() {}
}
