/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageDeliveryState;
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
}
