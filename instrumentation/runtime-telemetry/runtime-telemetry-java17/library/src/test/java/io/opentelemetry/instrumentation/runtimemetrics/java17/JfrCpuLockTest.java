/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrCpuLockTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder -> builder.disableAllFeatures().enableFeature(JfrFeature.LOCK_METRICS));

  @Test
  void shouldHaveLockEvents() throws Exception {
    AtomicBoolean done = new AtomicBoolean(false);
    synchronized (done) {
      new Thread(
              () -> {
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                }
                synchronized (done) {
                  done.set(true);
                  done.notifyAll();
                }
              })
          .start();
      long waitTime = Duration.ofSeconds(10).toMillis();
      long endTime = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
      while (!done.get() && waitTime > 0) {
        done.wait(waitTime);
        waitTime = endTime - System.currentTimeMillis();
      }
    }

    assertThat(done.get()).isEqualTo(true);

    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("jvm.cpu.longlock")
                .hasUnit(SECONDS)
                .hasHistogramSatisfying(histogram -> {}));
  }
}
