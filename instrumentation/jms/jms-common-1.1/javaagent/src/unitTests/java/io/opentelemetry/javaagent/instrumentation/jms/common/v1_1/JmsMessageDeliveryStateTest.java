/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageDeliveryState;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class JmsMessageDeliveryStateTest {

  @Test
  void consumedMessagesCanOnlyBeClaimedOnce() {
    JmsMessageDeliveryState state = new JmsMessageDeliveryState();

    assertThat(state.claimConsumedMessages()).isTrue();
    assertThat(state.claimConsumedMessages()).isFalse();
  }

  @Test
  void startsNewOwnershipForEachTopLevelProcessingCallback() {
    JmsMessageDeliveryState state = new JmsMessageDeliveryState();

    assertThat(state.beginProcessing()).isFalse();
    assertThat(state.claimConsumedMessages()).isTrue();
    assertThat(state.endProcessing()).isTrue();

    assertThat(state.beginProcessing()).isFalse();
    assertThat(state.claimConsumedMessages()).isTrue();
    assertThat(state.endProcessing()).isTrue();
  }

  @Test
  void preservesReceiveOwnershipThroughNestedProcessingCallbacks() {
    JmsMessageDeliveryState state = new JmsMessageDeliveryState();
    state.prepareForReceive();
    assertThat(state.claimConsumedMessages()).isTrue();

    assertThat(state.beginProcessing()).isTrue();
    assertThat(state.claimConsumedMessages()).isFalse();
    assertThat(state.beginProcessing()).isTrue();
    assertThat(state.claimConsumedMessages()).isFalse();
    assertThat(state.endProcessing()).isFalse();
    assertThat(state.endProcessing()).isTrue();
  }

  @Test
  void consumedMessagesCanOnlyBeClaimedOnceConcurrently()
      throws ExecutionException, InterruptedException {
    JmsMessageDeliveryState state = new JmsMessageDeliveryState();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Boolean> first =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return state.claimConsumedMessages();
              });
      Future<Boolean> second =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return state.claimConsumedMessages();
              });

      assertThat(ready.await(1, MINUTES)).isTrue();
      start.countDown();

      assertThat(asList(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
    } finally {
      executor.shutdownNow();
    }
  }
}
