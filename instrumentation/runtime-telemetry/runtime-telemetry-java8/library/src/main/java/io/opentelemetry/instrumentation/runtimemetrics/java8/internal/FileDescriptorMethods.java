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
public class FileDescriptorMethods {

  private static final String OS_BEAN_J9 = "com.ibm.lang.management.UnixOperatingSystemMXBean";
  private static final String OS_BEAN_HOTSPOT = "com.sun.management.UnixOperatingSystemMXBean";
  private static final String METHOD_OPEN_FILE_DESCRIPTOR_COUNT = "getOpenFileDescriptorCount";
  private static final String METHOD_MAX_FILE_DESCRIPTOR_COUNT = "getMaxFileDescriptorCount";

  @Nullable private static final Supplier<Long> openFileDescriptorCount;
  @Nullable private static final Supplier<Long> maxFileDescriptorCount;

  static {
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    Supplier<Long> openFileDescriptorCountSupplier =
        methodInvoker(osBean, OS_BEAN_HOTSPOT, METHOD_OPEN_FILE_DESCRIPTOR_COUNT);
    if (openFileDescriptorCountSupplier == null) {
      // More users will be on hotspot than j9, so check for j9 second
      openFileDescriptorCountSupplier =
          methodInvoker(osBean, OS_BEAN_J9, METHOD_OPEN_FILE_DESCRIPTOR_COUNT);
    }

    openFileDescriptorCount = openFileDescriptorCountSupplier;

    Supplier<Long> maxFileDescriptorCountSupplier =
        methodInvoker(osBean, OS_BEAN_HOTSPOT, METHOD_MAX_FILE_DESCRIPTOR_COUNT);
    if (maxFileDescriptorCountSupplier == null) {
      // More users will be on hotspot than j9, so check for j9 second
      maxFileDescriptorCountSupplier =
          methodInvoker(osBean, OS_BEAN_J9, METHOD_MAX_FILE_DESCRIPTOR_COUNT);
    }

    maxFileDescriptorCount = maxFileDescriptorCountSupplier;
  }

  @Nullable
  @SuppressWarnings("ReturnValueIgnored")
  private static Supplier<Long> methodInvoker(
      OperatingSystemMXBean osBean, String osBeanClassName, String methodName) {
    try {
      Class<?> osBeanClass = Class.forName(osBeanClassName);
      osBeanClass.cast(osBean);
      Method method = osBeanClass.getDeclaredMethod(methodName);
      return () -> {
        try {
          return (Long) method.invoke(osBean);
        } catch (IllegalAccessException | InvocationTargetException e) {
          return null;
        }
      };
    } catch (ClassNotFoundException | ClassCastException | NoSuchMethodException e) {
      return null;
    }
  }

  public static Supplier<Long> openFileDescriptorCount() {
    return openFileDescriptorCount;
  }

  public static Supplier<Long> maxFileDescriptorCount() {
    return maxFileDescriptorCount;
  }

  private FileDescriptorMethods() {}
}
