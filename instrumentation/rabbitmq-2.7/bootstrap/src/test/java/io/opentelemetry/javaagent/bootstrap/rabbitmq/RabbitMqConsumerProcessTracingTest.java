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

    Boolean previous = processSpanSuppression().set(Boolean.TRUE);
    try {
      assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isTrue();
    } finally {
      processSpanSuppression().restore(previous);
    }

    assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
  }

  @Test
  void shouldRestoreNestedRegistration() {
    Boolean outerPrevious = processSpanSuppression().set(Boolean.TRUE);
    try {
      Boolean innerPrevious = processSpanSuppression().set(Boolean.TRUE);
      try {
        assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isTrue();
      } finally {
        processSpanSuppression().restore(innerPrevious);
      }
      assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isTrue();
    } finally {
      processSpanSuppression().restore(outerPrevious);
    }

    assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
  }

  @Test
  void shouldCleanUpAfterException() {
    assertThatThrownBy(
            () -> {
              Boolean previous = processSpanSuppression().set(Boolean.TRUE);
              try {
                throw new IllegalStateException("test");
              } finally {
                processSpanSuppression().restore(previous);
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(RabbitMqConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
  }
}
