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

    Boolean previous = RabbitMqConsumerProcessTracing.setSpringProcessTelemetry(true);
    try {
      assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
    } finally {
      RabbitMqConsumerProcessTracing.restoreSpringProcessTelemetry(previous);
    }

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }

  @Test
  void shouldRestoreNestedRegistration() {
    Boolean outerPrevious = RabbitMqConsumerProcessTracing.setSpringProcessTelemetry(true);
    try {
      Boolean innerPrevious = RabbitMqConsumerProcessTracing.setSpringProcessTelemetry(true);
      try {
        assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
      } finally {
        RabbitMqConsumerProcessTracing.restoreSpringProcessTelemetry(innerPrevious);
      }
      assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
    } finally {
      RabbitMqConsumerProcessTracing.restoreSpringProcessTelemetry(outerPrevious);
    }

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }

  @Test
  void shouldPreserveOuterRegistrationWhenNestedRegistrationIsNotSpringOwned() {
    Boolean outerPrevious = RabbitMqConsumerProcessTracing.setSpringProcessTelemetry(true);
    try {
      Boolean innerPrevious = RabbitMqConsumerProcessTracing.setSpringProcessTelemetry(false);
      try {
        assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
      } finally {
        RabbitMqConsumerProcessTracing.restoreSpringProcessTelemetry(innerPrevious);
      }
      assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isFalse();
    } finally {
      RabbitMqConsumerProcessTracing.restoreSpringProcessTelemetry(outerPrevious);
    }

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }

  @Test
  void shouldCleanUpAfterException() {
    assertThatThrownBy(
            () -> {
              Boolean previous = RabbitMqConsumerProcessTracing.setSpringProcessTelemetry(true);
              try {
                throw new IllegalStateException("test");
              } finally {
                RabbitMqConsumerProcessTracing.restoreSpringProcessTelemetry(previous);
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(RabbitMqConsumerProcessTracing.shouldTraceProcess()).isTrue();
  }
}
