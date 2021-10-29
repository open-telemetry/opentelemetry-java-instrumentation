/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import java.util.concurrent.atomic.AtomicInteger;

public final class MuzzleFailureCounter {
  private static final AtomicInteger counter = new AtomicInteger();

  private MuzzleFailureCounter() {}

  public static int getAndReset() {
    return counter.getAndSet(0);
  }

  public static void inc() {
    counter.incrementAndGet();
  }
}
