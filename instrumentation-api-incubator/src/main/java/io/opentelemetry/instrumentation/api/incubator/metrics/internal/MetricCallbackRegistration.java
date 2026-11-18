/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.metrics.internal;

import java.util.function.Supplier;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MetricCallbackRegistration {

  /**
   * Calls {@code registration} with a null thread context class loader and restores the original
   * loader even if registration fails. Pass only the registration call, not builder or factory
   * setup. The meter implementation determines the class loader during collection.
   */
  public static <T> T withoutContextClassLoader(Supplier<T> registration) {
    Thread thread = Thread.currentThread();
    ClassLoader originalClassLoader = thread.getContextClassLoader();
    thread.setContextClassLoader(null);
    try {
      return registration.get();
    } finally {
      thread.setContextClassLoader(originalClassLoader);
    }
  }

  private MetricCallbackRegistration() {}
}
