/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal.cache.weaklockfree;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class WeakConcurrentMapCleaner {
  @Nullable private static Thread thread;

  private WeakConcurrentMapCleaner() {}

  public static synchronized void start() {
    if (thread != null) {
      return;
    }

    thread = new Thread(AbstractWeakConcurrentMap::runCleanup, "weak-ref-cleaner");
    thread.setDaemon(true);
    thread.setContextClassLoader(null);
    thread.start();
  }

  @SuppressWarnings("Interruption")
  public static synchronized void stop() {
    if (thread == null) {
      return;
    }

    thread.interrupt();
    thread = null;
  }
}
