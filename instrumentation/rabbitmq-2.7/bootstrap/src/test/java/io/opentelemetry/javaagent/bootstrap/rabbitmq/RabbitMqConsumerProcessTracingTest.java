/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.processSpanSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RabbitMqConsumerProcessTracingTest {

  @Test
  void shouldScopeSpringProcessTelemetryOwnership() {
    assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();

    boolean suppressionAcquired = processSpanSuppression().tryAcquire();
    try {
      assertThat(suppressionAcquired).isTrue();
      assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isTrue();
    } finally {
      if (suppressionAcquired) {
        processSpanSuppression().release();
      }
    }

    assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
  }

  @Test
  void shouldRestoreNestedRegistration() {
    boolean outerSuppressionAcquired = processSpanSuppression().tryAcquire();
    try {
      assertThat(outerSuppressionAcquired).isTrue();

      boolean innerSuppressionAcquired = processSpanSuppression().tryAcquire();
      try {
        assertThat(innerSuppressionAcquired).isFalse();
        assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isTrue();
      } finally {
        if (innerSuppressionAcquired) {
          processSpanSuppression().release();
        }
      }
      assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isTrue();
    } finally {
      if (outerSuppressionAcquired) {
        processSpanSuppression().release();
      }
    }

    assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
  }

  @Test
  void shouldCleanUpAfterException() {
    assertThatThrownBy(
            () -> {
              boolean suppressionAcquired = processSpanSuppression().tryAcquire();
              try {
                assertThat(suppressionAcquired).isTrue();
                throw new IllegalStateException("test");
              } finally {
                if (suppressionAcquired) {
                  processSpanSuppression().release();
                }
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
  }
}
