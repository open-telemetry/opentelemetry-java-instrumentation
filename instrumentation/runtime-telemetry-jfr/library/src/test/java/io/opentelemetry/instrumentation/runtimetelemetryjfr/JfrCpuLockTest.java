/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.MILLISECONDS;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrCpuLockTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder -> builder.disableAllFeatures().enableFeature(JfrFeature.LOCK_METRICS));

  @Test
  void shouldHaveLockEvents() throws Exception {
    // This should generate some events
    System.gc();
    synchronized (this) {
      Thread.sleep(1000);
    }

    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("process.runtime.jvm.cpu.longlock")
                .hasUnit(MILLISECONDS)
                .hasHistogramSatisfying(histogram -> {}));
  }
}
