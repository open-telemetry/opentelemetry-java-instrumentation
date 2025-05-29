/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;

public final class GcUtils {

  private static final StringBuilder garbage = new StringBuilder();

  public static void awaitGc(Duration timeout) throws InterruptedException, TimeoutException {
    Object obj = new Object();
    WeakReference<Object> ref = new WeakReference<>(obj);
    obj = null;
    awaitGc(ref, timeout);
  }

  public static void awaitGc(WeakReference<?> ref, Duration timeout)
      throws InterruptedException, TimeoutException {
    long start = System.currentTimeMillis();
    while (ref.get() != null
        && !timeout.minus(System.currentTimeMillis() - start, ChronoUnit.MILLIS).isNegative()) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      // generate garbage to give gc some work
      for (int i = 0; i < 26; i++) {
        if (garbage.length() == 0) {
          garbage.append("ab");
        } else {
          garbage.append(garbage);
        }
      }
      garbage.setLength(0);
      System.gc();
    }
    if (ref.get() != null) {
      throw new TimeoutException("reference was not cleared in time");
    }
  }

  private GcUtils() {}
}
