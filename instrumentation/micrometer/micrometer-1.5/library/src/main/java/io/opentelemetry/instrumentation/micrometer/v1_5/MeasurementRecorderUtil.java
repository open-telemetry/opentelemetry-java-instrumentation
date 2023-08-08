/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

class MeasurementRecorderUtil {

  static void runInThreadContextClassLoader(ClassLoader loader, Runnable runnable) {
    ClassLoader prior = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(loader);
    try {
      runnable.run();
    } finally {
      Thread.currentThread().setContextClassLoader(prior);
    }
  }

  private MeasurementRecorderUtil() {}
}
