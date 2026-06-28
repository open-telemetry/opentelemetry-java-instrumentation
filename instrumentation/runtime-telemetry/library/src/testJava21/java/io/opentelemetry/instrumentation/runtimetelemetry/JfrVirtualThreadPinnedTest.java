/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrVirtualThreadPinnedTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          jfrConfig -> {
            jfrConfig.disableAllFeatures();
            jfrConfig.enableFeature(JfrFeature.VIRTUAL_THREAD_METRICS);
          });

  @Test
  void shouldHaveVirtualThreadPinnedEvents() throws InterruptedException {
    // Thread.ofVirtual() is GA in Java 21; synchronized pinning was removed in Java 24 (JEP 491)
    int feature = Runtime.version().feature();
    Assumptions.assumeTrue(feature >= 21 && feature < 24, "Requires Java 21-23");

    CountDownLatch latch = new CountDownLatch(1);

    // Run a synchronized block inside a virtual thread to trigger pinning
    Object lock = new Object();
    Thread vt =
        Thread.ofVirtual()
            .start(
                () -> {
                  synchronized (lock) {
                    // Parking inside a synchronized block pins the virtual thread
                    LockSupport.parkNanos(100_000_000L); // 100ms
                  }
                  latch.countDown();
                });

    assertThat(latch.await(10, SECONDS)).isTrue();
    vt.join(5000);

    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("jvm.thread.virtual.pinned")
                .hasUnit(Constants.SECONDS)
                .hasHistogramSatisfying(histogram -> histogram.hasPointsSatisfying(point -> {})));
  }
}
