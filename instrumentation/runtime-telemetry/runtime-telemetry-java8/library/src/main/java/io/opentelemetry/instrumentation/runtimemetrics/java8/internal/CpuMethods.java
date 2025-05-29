/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CpuMethods {

  private static final String OS_BEAN_J9 = "com.ibm.lang.management.OperatingSystemMXBean";
  private static final String OS_BEAN_HOTSPOT = "com.sun.management.OperatingSystemMXBean";
  private static final String METHOD_PROCESS_CPU_TIME = "getProcessCpuTime";
  private static final String METHOD_PROCESS_CPU_LOAD = "getProcessCpuLoad";
  private static final String METHOD_CPU_LOAD = "getCpuLoad";
  private static final String METHOD_SYSTEM_CPU_LOAD = "getSystemCpuLoad";

  @Nullable private static final Supplier<Long> processCpuTime;
  @Nullable private static final Supplier<Double> processCpuUtilization;
  @Nullable private static final Supplier<Double> systemCpuUtilization;

  static {
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    Supplier<Long> processCpuTimeSupplier =
        methodInvoker(osBean, OS_BEAN_HOTSPOT, METHOD_PROCESS_CPU_TIME, Long.class);
    if (processCpuTimeSupplier == null) {
      // More users will be on hotspot than j9, so check for j9 second
      processCpuTimeSupplier =
          methodInvoker(osBean, OS_BEAN_J9, METHOD_PROCESS_CPU_TIME, Long.class);
    }
    processCpuTime = processCpuTimeSupplier;

    Supplier<Double> processCpuSupplier =
        methodInvoker(osBean, OS_BEAN_HOTSPOT, METHOD_PROCESS_CPU_LOAD, Double.class);
    if (processCpuSupplier == null) {
      // More users will be on hotspot than j9, so check for j9 second
      processCpuSupplier = methodInvoker(osBean, OS_BEAN_J9, METHOD_PROCESS_CPU_LOAD, Double.class);
    }
    processCpuUtilization = processCpuSupplier;

    // As of java 14, com.sun.management.OperatingSystemMXBean#getCpuLoad() is preferred and
    // #getSystemCpuLoad() is deprecated
    Supplier<Double> systemCpuSupplier =
        methodInvoker(osBean, OS_BEAN_HOTSPOT, METHOD_CPU_LOAD, Double.class);
    if (systemCpuSupplier == null) {
      systemCpuSupplier =
          methodInvoker(osBean, OS_BEAN_HOTSPOT, METHOD_SYSTEM_CPU_LOAD, Double.class);
    }
    if (systemCpuSupplier == null) {
      // More users will be on hotspot than j9, so check for j9 second
      systemCpuSupplier = methodInvoker(osBean, OS_BEAN_J9, METHOD_SYSTEM_CPU_LOAD, Double.class);
    }
    systemCpuUtilization = systemCpuSupplier;
  }

  @Nullable
  @SuppressWarnings("ReturnValueIgnored")
  private static <T extends Number> Supplier<T> methodInvoker(
      OperatingSystemMXBean osBean,
      String osBeanClassName,
      String methodName,
      Class<T> returnType) {
    try {
      Class<?> osBeanClass = Class.forName(osBeanClassName);
      osBeanClass.cast(osBean);
      Method method = osBeanClass.getDeclaredMethod(methodName);
      return () -> {
        try {
          return returnType.cast(method.invoke(osBean));
        } catch (IllegalAccessException | InvocationTargetException e) {
          return null;
        }
      };
    } catch (ClassNotFoundException | ClassCastException | NoSuchMethodException e) {
      return null;
    }
  }

  public static Supplier<Long> processCpuTime() {
    return processCpuTime;
  }

  public static Supplier<Double> processCpuUtilization() {
    return processCpuUtilization;
  }

  public static Supplier<Double> systemCpuUtilization() {
    return systemCpuUtilization;
  }

  private CpuMethods() {}
}
