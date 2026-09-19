/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import com.sun.management.UnixOperatingSystemMXBean;
import io.opentelemetry.api.metrics.Meter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class FileDescriptor {
  private static final Class<?> unixOperatingSystemMxBeanClass =
      loadClass("com.sun.management.UnixOperatingSystemMXBean");

  private static Class<?> loadClass(String className) {
    try {
      return Class.forName(className, false, FileDescriptor.class.getClassLoader());
    } catch (ClassNotFoundException | LinkageError e) {
      return null;
    }
  }

  /** Register observers for java runtime file descriptor metrics. */
  public static List<AutoCloseable> registerObservers(Meter meter) {
    return registerObservers(meter, unused -> true);
  }

  static List<AutoCloseable> registerObservers(Meter meter, Predicate<String> metricNamePredicate) {
    return registerObservers(
        meter, ManagementFactory.getOperatingSystemMXBean(), metricNamePredicate);
  }

  // Visible for testing
  static List<AutoCloseable> registerObservers(Meter meter, OperatingSystemMXBean osBean) {
    return registerObservers(meter, osBean, unused -> true);
  }

  private static List<AutoCloseable> registerObservers(
      Meter meter, OperatingSystemMXBean osBean, Predicate<String> metricNamePredicate) {
    List<AutoCloseable> observables = new ArrayList<>();

    if (unixOperatingSystemMxBeanClass != null
        && unixOperatingSystemMxBeanClass.isInstance(osBean)) {
      if (metricNamePredicate.test("jvm.file_descriptor.count")) {
        observables.add(
            meter
                .upDownCounterBuilder("jvm.file_descriptor.count")
                .setDescription("Number of open file descriptors as reported by the JVM.")
                .setUnit("{file_descriptor}")
                .buildWithCallback(
                    observableMeasurement -> {
                      long value =
                          ((UnixOperatingSystemMXBean) osBean).getOpenFileDescriptorCount();
                      if (value >= 0) {
                        observableMeasurement.record(value);
                      }
                    }));
      }
      if (metricNamePredicate.test("jvm.file_descriptor.limit")) {
        observables.add(
            meter
                .upDownCounterBuilder("jvm.file_descriptor.limit")
                .setDescription("Measure of max open file descriptors as reported by the JVM.")
                .setUnit("{file_descriptor}")
                .buildWithCallback(
                    observableMeasurement -> {
                      long value = ((UnixOperatingSystemMXBean) osBean).getMaxFileDescriptorCount();
                      if (value >= 0) {
                        observableMeasurement.record(value);
                      }
                    }));
      }
    }

    return observables;
  }

  private FileDescriptor() {}
}
