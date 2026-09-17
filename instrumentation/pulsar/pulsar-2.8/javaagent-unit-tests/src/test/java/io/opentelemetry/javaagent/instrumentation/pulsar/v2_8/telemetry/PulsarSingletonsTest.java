/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.listenerReceiveSpanSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadSuppression;
import org.junit.jupiter.api.Test;

class PulsarSingletonsTest {

  @Test
  void nestedReceiveSpanSuppressionPreservesOuterSuppression() {
    ScopedThreadSuppression suppression = listenerReceiveSpanSuppression();
    boolean outerSuppressionAcquired = suppression.tryAcquire();
    try {
      assertThat(outerSuppressionAcquired).isTrue();
      boolean innerSuppressionAcquired = suppression.tryAcquire();
      assertThat(innerSuppressionAcquired).isFalse();
      if (innerSuppressionAcquired) {
        suppression.release();
      }
      assertThat(suppression.isActive()).isTrue();
    } finally {
      if (outerSuppressionAcquired) {
        suppression.release();
      }
    }
    assertThat(suppression.isActive()).isFalse();
  }

  @Test
  void receiveSpanSuppressionReleasesAfterException() {
    ScopedThreadSuppression suppression = listenerReceiveSpanSuppression();
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

    assertThat(suppression.isActive()).isFalse();
  }
}
