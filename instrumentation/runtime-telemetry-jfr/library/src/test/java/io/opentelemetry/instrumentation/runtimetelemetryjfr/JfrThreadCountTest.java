/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.DAEMON;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.UNIT_THREADS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrThreadCountTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder -> builder.disableAllFeatures().enableFeature(JfrFeature.THREAD_METRICS));

  @Test
  void shouldHaveJfrThreadCountEvents() throws Exception {
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
                .hasName("process.runtime.jvm.threads.count")
                .hasUnit(UNIT_THREADS)
                .satisfies(
                    data ->
                        assertThat(data.getLongSumData().getPoints())
                            .anyMatch(p -> p.getValue() > 0 && !isDaemon(p))
                            .anyMatch(p -> p.getValue() > 0 && isDaemon(p))));
  }

  private static boolean isDaemon(LongPointData p) {
    return Objects.requireNonNull(p.getAttributes().get(AttributeKey.booleanKey(DAEMON)));
  }
}
