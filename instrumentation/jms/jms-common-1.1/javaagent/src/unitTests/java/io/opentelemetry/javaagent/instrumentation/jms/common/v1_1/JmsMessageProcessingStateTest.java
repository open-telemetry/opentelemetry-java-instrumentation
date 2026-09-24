/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageProcessingState;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JmsMessageProcessingStateTest {

  @Test
  void reportsOnlyFirstProcessingObserverPerDelivery() {
    JmsMessageProcessingState state = new JmsMessageProcessingState();

    assertThat(state.beginProcessing()).isTrue();
    assertThat(state.beginProcessing()).isFalse();
    assertThat(state.endProcessing()).isFalse();
    assertThat(state.endProcessing()).isTrue();
    assertThat(state.isProcessingCompleted()).isTrue();

    assertThat(state.beginProcessing()).isFalse();
    assertThat(state.endProcessing()).isFalse();
  }

  @Test
  void coordinatesConcurrentProcessingObservers() throws ExecutionException, InterruptedException {
    JmsMessageProcessingState state = new JmsMessageProcessingState();
    CyclicBarrier barrier = new CyclicBarrier(2);
    AtomicInteger firstObservers = new AtomicInteger();
    AtomicInteger finalObservers = new AtomicInteger();
    Callable<Void> observer =
        () -> {
          barrier.await();
          if (state.beginProcessing()) {
            firstObservers.incrementAndGet();
          }
          barrier.await();
          if (state.endProcessing()) {
            finalObservers.incrementAndGet();
          }
          return null;
        };

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      for (Future<Void> future : executor.invokeAll(asList(observer, observer))) {
        future.get();
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(firstObservers).hasValue(1);
    assertThat(finalObservers).hasValue(1);
    assertThat(state.isProcessingCompleted()).isTrue();
  }
}
