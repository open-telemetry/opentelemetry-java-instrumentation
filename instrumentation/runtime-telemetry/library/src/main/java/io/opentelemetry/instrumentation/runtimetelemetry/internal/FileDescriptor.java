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
    return registerObservers(meter, ManagementFactory.getOperatingSystemMXBean());
  }

  // Visible for testing
  static List<AutoCloseable> registerObservers(Meter meter, OperatingSystemMXBean osBean) {
    List<AutoCloseable> observables = new ArrayList<>();

    if (unixOperatingSystemMxBeanClass != null
        && unixOperatingSystemMxBeanClass.isInstance(osBean)) {
      observables.add(
          meter
              .upDownCounterBuilder("jvm.file_descriptor.count")
              .setDescription("Number of open file descriptors as reported by the JVM.")
              .setUnit("{file_descriptor}")
              .buildWithCallback(
                  observableMeasurement -> {
                    long openFileDescriptorCount =
                        ((UnixOperatingSystemMXBean) osBean).getOpenFileDescriptorCount();
                    if (openFileDescriptorCount >= 0) {
                      observableMeasurement.record(openFileDescriptorCount);
                    }
                  }));
    }

    return observables;
  }

  private FileDescriptor() {}
}
