/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.ATTR_DAEMON;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.UNIT_THREADS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrThreadCountTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          jfrConfig -> {
            jfrConfig.disableAllFeatures();
            jfrConfig.enableFeature(JfrFeature.THREAD_METRICS);
          });

  @Test
  void shouldHaveJfrThreadCountEvents() throws InterruptedException {
    Runnable work =
        () -> {
          try {
            // Sleep enough to produce events, based on ThreadCountHandler#getPollingDuration()
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        };
    Thread userThread = new Thread(work);
    userThread.setDaemon(false);
    userThread.start();

    Thread daemonThread = new Thread(work);
    daemonThread.setDaemon(true);
    daemonThread.start();

    userThread.join();
    daemonThread.join();

    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("jvm.thread.count")
                .hasUnit(UNIT_THREADS)
                .hasLongSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point
                                    .hasAttributesSatisfying(equalTo(ATTR_DAEMON, false))
                                    .hasValueSatisfying(v -> v.isPositive()),
                            point ->
                                point
                                    .hasAttributesSatisfying(equalTo(ATTR_DAEMON, true))
                                    .hasValueSatisfying(v -> v.isPositive()))));
  }
}
