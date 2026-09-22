/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageProcessingState;
import org.junit.jupiter.api.Test;

class JmsMessageProcessingStateTest {

  @Test
  void selectsOnlyFirstNestedProcessingObserver() {
    JmsMessageProcessingState state = new JmsMessageProcessingState();

    assertThat(state.beginProcessing()).isTrue();
    assertThat(state.beginProcessing()).isFalse();
    assertThat(state.endProcessing()).isFalse();
    assertThat(state.endProcessing()).isTrue();
    assertThat(state.isProcessingCompleted()).isTrue();

    assertThat(state.beginProcessing()).isFalse();
    assertThat(state.endProcessing()).isFalse();
  }
}
