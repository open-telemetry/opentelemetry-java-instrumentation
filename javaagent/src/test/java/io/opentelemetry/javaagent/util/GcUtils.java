/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.util;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;

public final class GcUtils {
  public static void awaitGc(WeakReference<?> ref, Duration timeout)
      throws InterruptedException, TimeoutException {
    long start = System.currentTimeMillis();
    while (ref.get() != null
        && !timeout.minus(System.currentTimeMillis() - start, ChronoUnit.MILLIS).isNegative()) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      System.gc();
      System.runFinalization();
    }
    if (ref.get() != null) {
      throw new TimeoutException("reference was not cleared in time");
    }
  }

  private GcUtils() {}
}
