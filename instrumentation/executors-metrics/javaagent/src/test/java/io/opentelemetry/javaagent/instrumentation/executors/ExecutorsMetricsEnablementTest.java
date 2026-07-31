/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ExecutorsMetricsEnablementTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.executors-metrics";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void emitsMetricsAccordingToConfiguration() throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new NamedThreadFactory("enablement-pool-42-worker"));

    try {
      assertThat(executor.prestartCoreThread()).isTrue();

      if (Boolean.getBoolean("test.metrics.expected")) {
        JvmExecutorMetricsAssertions.create(
                testing,
                INSTRUMENTATION_NAME,
                System.getProperty("test.executor.name", "enablement-pool-*-worker-*"),
                ThreadPoolExecutor.class.getName())
            .withCoreThreads(1)
            .assertExecutorEmitsMetrics();
      } else {
        assertNoExecutorMetrics("enablement-pool-*-worker-*");
      }
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  private static void assertNoExecutorMetrics(String executorName) {
    testing.clearData();
    testing
        .getOpenTelemetry()
        .getMeter("test")
        .counterBuilder("test.executor.metrics.collection")
        .build()
        .add(1);
    testing.waitAndAssertMetrics(
        "test", "test.executor.metrics.collection", metrics -> metrics.isNotEmpty());

    assertThat(testing.metrics())
        .filteredOn(metric -> metric.getName().startsWith("jvm.executor."))
        .allSatisfy(
            metric ->
                assertThat(metric.getLongSumData().getPoints())
                    .noneMatch(
                        point ->
                            executorName.equals(
                                point.getAttributes().get(stringKey("jvm.executor.name")))));
  }

  private static final class NamedThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final AtomicInteger sequence = new AtomicInteger();

    private NamedThreadFactory(String namePrefix) {
      this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
      return new Thread(runnable, namePrefix + "-" + sequence.incrementAndGet());
    }
  }
}
