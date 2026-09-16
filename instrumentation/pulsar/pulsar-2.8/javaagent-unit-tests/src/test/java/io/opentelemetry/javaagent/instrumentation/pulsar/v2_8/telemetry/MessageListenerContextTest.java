/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CLIENT_OPERATION_DURATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.MessageListenerContext.currentReceiveSpanSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetrySuppression;
import org.junit.jupiter.api.Test;

class MessageListenerContextTest {

  @Test
  void nestedReceiveSpanSuppressionRestoresPreviousSuppression() {
    MessagingTelemetrySuppression suppression = currentReceiveSpanSuppression();
    MessagingTelemetrySignals beforeOuter = suppression.suppress(RECEIVE, SPAN);
    try {
      MessagingTelemetrySignals beforeInner = suppression.suppress(RECEIVE, SPAN);
      suppression.restore(beforeInner);
      assertThat(MessageListenerContext.isProcessing()).isTrue();
      assertThat(suppression.isSuppressed(RECEIVE, CLIENT_OPERATION_DURATION)).isFalse();
      assertThat(suppression.isSuppressed(RECEIVE, CONSUMED_MESSAGES)).isFalse();
    } finally {
      suppression.restore(beforeOuter);
    }
    assertThat(MessageListenerContext.isProcessing()).isFalse();
  }

  @Test
  void receiveSpanSuppressionRestoresAfterException() {
    MessagingTelemetrySuppression suppression = currentReceiveSpanSuppression();
    MessagingTelemetrySignals previous = suppression.suppress(RECEIVE, SPAN);

    assertThatIllegalStateException()
        .isThrownBy(
            () -> {
              try {
                throw new IllegalStateException("boom");
              } finally {
                suppression.restore(previous);
              }
            });

    assertThat(MessageListenerContext.isProcessing()).isFalse();
  }
}
