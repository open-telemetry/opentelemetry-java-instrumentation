/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RabbitMqConsumerProcessTracingTest {

  @Test
  void shouldScopeSpringProcessTelemetryOwnership() {
    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();

    try (RabbitMqConsumerProcessTracing.Registration ignored =
        RabbitMqConsumerProcessTracing.startSpringProcessTelemetry()) {
      assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
    }

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }

  @Test
  void shouldRestoreNestedRegistration() {
    try (RabbitMqConsumerProcessTracing.Registration ignored =
        RabbitMqConsumerProcessTracing.startSpringProcessTelemetry()) {
      try (RabbitMqConsumerProcessTracing.Registration nested =
          RabbitMqConsumerProcessTracing.startSpringProcessTelemetry()) {
        assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
      }
      assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
    }

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }

  @Test
  void shouldCleanUpAfterException() {
    assertThatThrownBy(
            () -> {
              try (RabbitMqConsumerProcessTracing.Registration ignored =
                  RabbitMqConsumerProcessTracing.startSpringProcessTelemetry()) {
                throw new IllegalStateException("test");
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }
}
