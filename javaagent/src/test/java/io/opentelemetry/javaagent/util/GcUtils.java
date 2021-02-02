/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.util;

import java.lang.ref.WeakReference;

public final class GcUtils {
  public static void awaitGc(WeakReference<?> ref) throws InterruptedException {
    while (ref.get() != null) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      System.gc();
      System.runFinalization();
    }
  }

  private GcUtils() {}
}
