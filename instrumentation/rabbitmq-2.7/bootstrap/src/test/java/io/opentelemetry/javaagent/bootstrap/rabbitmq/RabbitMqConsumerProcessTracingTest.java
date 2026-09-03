/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RabbitMqConsumerProcessTracingTest {

  @Test
  void shouldRestorePreviousWrappingState() {
    boolean previous = RabbitMqConsumerProcessTracing.setWrappingEnabled(false);

    assertThat(previous).isTrue();
    assertThat(RabbitMqConsumerProcessTracing.isWrappingEnabled()).isFalse();

    RabbitMqConsumerProcessTracing.setWrappingEnabled(previous);
    assertThat(RabbitMqConsumerProcessTracing.isWrappingEnabled()).isTrue();
  }

  @Test
  void shouldReturnDisabledPreviousState() {
    boolean outerPrevious = RabbitMqConsumerProcessTracing.setWrappingEnabled(false);
    boolean innerPrevious = RabbitMqConsumerProcessTracing.setWrappingEnabled(false);

    assertThat(outerPrevious).isTrue();
    assertThat(innerPrevious).isFalse();

    RabbitMqConsumerProcessTracing.setWrappingEnabled(innerPrevious);
    assertThat(RabbitMqConsumerProcessTracing.isWrappingEnabled()).isFalse();

    RabbitMqConsumerProcessTracing.setWrappingEnabled(outerPrevious);
    assertThat(RabbitMqConsumerProcessTracing.isWrappingEnabled()).isTrue();
  }
}
