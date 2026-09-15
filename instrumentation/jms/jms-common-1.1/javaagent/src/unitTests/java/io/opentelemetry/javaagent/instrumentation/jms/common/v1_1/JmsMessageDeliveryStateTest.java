/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageDeliveryState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class JmsMessageDeliveryStateTest {

  @Test
  void consumedMessagesCanOnlyBeClaimedOnce() throws Exception {
    JmsMessageDeliveryState state = new JmsMessageDeliveryState();
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Boolean>> claims = new ArrayList<>();
      for (int i = 0; i < 32; i++) {
        claims.add(state::claimConsumedMessages);
      }

      int successfulClaims = 0;
      for (Future<Boolean> result : executor.invokeAll(claims)) {
        if (result.get()) {
          successfulClaims++;
        }
      }

      assertThat(successfulClaims).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
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
}
