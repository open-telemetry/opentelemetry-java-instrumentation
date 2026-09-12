/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import org.junit.jupiter.api.Test;

class MessageListenerContextTest {

  @Test
  void nestedProcessingRestoresPreviousClaim() {
    MessagingTelemetrySignals beforeOuter = MessageListenerContext.startProcessing();
    MessagingTelemetrySignals beforeInner = MessageListenerContext.startProcessing();

    MessageListenerContext.endProcessing(beforeInner);
    assertThat(MessageListenerContext.isProcessing()).isTrue();

    MessageListenerContext.endProcessing(beforeOuter);
    assertThat(MessageListenerContext.isProcessing()).isFalse();
  }
}
