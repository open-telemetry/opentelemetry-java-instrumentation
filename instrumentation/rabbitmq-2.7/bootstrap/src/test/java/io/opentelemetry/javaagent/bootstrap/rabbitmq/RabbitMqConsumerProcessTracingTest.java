/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.rabbitProcessTracingSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RabbitMqConsumerProcessTracingTest {

  @Test
  void shouldScopeSpringProcessTelemetryOwnership() {
    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();

    Boolean previous = rabbitProcessTracingSuppression().set(Boolean.TRUE);
    try {
      assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
    } finally {
      rabbitProcessTracingSuppression().restore(previous);
    }

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }

  @Test
  void shouldRestoreNestedRegistration() {
    Boolean outerPrevious = rabbitProcessTracingSuppression().set(Boolean.TRUE);
    try {
      Boolean innerPrevious = rabbitProcessTracingSuppression().set(Boolean.TRUE);
      try {
        assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
      } finally {
        rabbitProcessTracingSuppression().restore(innerPrevious);
      }
      assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
    } finally {
      rabbitProcessTracingSuppression().restore(outerPrevious);
    }

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }

  @Test
  void shouldCleanUpAfterException() {
    assertThatThrownBy(
            () -> {
              Boolean previous = rabbitProcessTracingSuppression().set(Boolean.TRUE);
              try {
                throw new IllegalStateException("test");
              } finally {
                rabbitProcessTracingSuppression().restore(previous);
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }
}
