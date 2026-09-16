/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.currentProcessSpanSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RabbitMqConsumerProcessTracingTest {

  @Test
  void shouldScopeSpringProcessTelemetryOwnership() {
    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();

    Boolean previous = currentProcessSpanSuppression().set(Boolean.TRUE);
    try {
      assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
    } finally {
      currentProcessSpanSuppression().restore(previous);
    }

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }

  @Test
  void shouldRestoreNestedRegistration() {
    Boolean outerPrevious = currentProcessSpanSuppression().set(Boolean.TRUE);
    try {
      Boolean innerPrevious = currentProcessSpanSuppression().set(Boolean.TRUE);
      try {
        assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
      } finally {
        currentProcessSpanSuppression().restore(innerPrevious);
      }
      assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
    } finally {
      currentProcessSpanSuppression().restore(outerPrevious);
    }

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }

  @Test
  void shouldCleanUpAfterException() {
    assertThatThrownBy(
            () -> {
              Boolean previous = currentProcessSpanSuppression().set(Boolean.TRUE);
              try {
                throw new IllegalStateException("test");
              } finally {
                currentProcessSpanSuppression().restore(previous);
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }
}
