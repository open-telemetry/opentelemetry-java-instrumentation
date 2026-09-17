/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.MessageListenerContext.receiveSpanSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadSuppression;
import org.junit.jupiter.api.Test;

class MessageListenerContextTest {

  @Test
  void nestedReceiveSpanSuppressionPreservesOuterSuppression() {
    ScopedThreadSuppression suppression = receiveSpanSuppression();
    boolean outerSuppressionAcquired = suppression.tryAcquire();
    try {
      assertThat(outerSuppressionAcquired).isTrue();
      boolean innerSuppressionAcquired = suppression.tryAcquire();
      assertThat(innerSuppressionAcquired).isFalse();
      if (innerSuppressionAcquired) {
        suppression.release();
      }
      assertThat(MessageListenerContext.isReceiveSpanSuppressed()).isTrue();
    } finally {
      if (outerSuppressionAcquired) {
        suppression.release();
      }
    }
    assertThat(MessageListenerContext.isReceiveSpanSuppressed()).isFalse();
  }

  @Test
  void receiveSpanSuppressionReleasesAfterException() {
    ScopedThreadSuppression suppression = receiveSpanSuppression();
    boolean suppressionAcquired = suppression.tryAcquire();

    assertThatIllegalStateException()
        .isThrownBy(
            () -> {
              try {
                throw new IllegalStateException("boom");
              } finally {
                if (suppressionAcquired) {
                  suppression.release();
                }
              }
            });

    assertThat(MessageListenerContext.isReceiveSpanSuppressed()).isFalse();
  }
}
