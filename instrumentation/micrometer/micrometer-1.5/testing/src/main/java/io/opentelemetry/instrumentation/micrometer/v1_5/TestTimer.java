/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import java.util.concurrent.TimeUnit;

class TestTimer {
  private int count = 0;
  private long totalTimeNanos = 0;

  void add(long time, TimeUnit unit) {
    count++;
    totalTimeNanos += unit.toNanos(time);
  }

  int getCount() {
    return count;
  }

  double getTotalTimeNanos() {
    return totalTimeNanos;
  }

  void reset() {
    count = 0;
    totalTimeNanos = 0;
  }
}
