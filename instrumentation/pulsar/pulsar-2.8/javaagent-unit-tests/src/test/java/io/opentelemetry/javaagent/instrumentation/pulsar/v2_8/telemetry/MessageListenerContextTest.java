/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.MessageListenerContext.receiveSpanSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import org.junit.jupiter.api.Test;

class MessageListenerContextTest {

  @Test
  void nestedReceiveSpanSuppressionRestoresPreviousSuppression() {
    ScopedThreadValue<Boolean> suppression = receiveSpanSuppression();
    Boolean beforeOuter = suppression.set(Boolean.TRUE);
    try {
      Boolean beforeInner = suppression.set(Boolean.TRUE);
      suppression.restore(beforeInner);
      assertThat(MessageListenerContext.isReceiveSpanSuppressed()).isTrue();
    } finally {
      suppression.restore(beforeOuter);
    }
    assertThat(MessageListenerContext.isReceiveSpanSuppressed()).isFalse();
  }

  @Test
  void receiveSpanSuppressionRestoresAfterException() {
    ScopedThreadValue<Boolean> suppression = receiveSpanSuppression();
    Boolean previous = suppression.set(Boolean.TRUE);

    assertThatIllegalStateException()
        .isThrownBy(
            () -> {
              try {
                throw new IllegalStateException("boom");
              } finally {
                suppression.restore(previous);
              }
            });

    assertThat(MessageListenerContext.isReceiveSpanSuppressed()).isFalse();
  }
}
