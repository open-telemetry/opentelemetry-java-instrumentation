/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import org.junit.jupiter.api.Test;

class RabbitMqConsumerProcessTracingTest {

  @Test
  void shouldRestorePreviousClaims() {
    MessagingTelemetrySignals previous = RabbitMqConsumerProcessTracing.suppress(PROCESS, SPAN);

    assertThat(previous).isEqualTo(MessagingTelemetrySignals.none());
    assertThat(RabbitMqConsumerProcessTracing.isSuppressed(PROCESS, SPAN)).isTrue();

    RabbitMqConsumerProcessTracing.restore(previous);
    assertThat(RabbitMqConsumerProcessTracing.isSuppressed(PROCESS, SPAN)).isFalse();
  }

  @Test
  void shouldKeepClaimsIndependentWhenNested() {
    MessagingTelemetrySignals outerPrevious =
        RabbitMqConsumerProcessTracing.suppress(PROCESS, SPAN);
    MessagingTelemetrySignals innerPrevious =
        RabbitMqConsumerProcessTracing.suppress(RECEIVE, CONSUMED_MESSAGES);

    assertThat(outerPrevious).isEqualTo(MessagingTelemetrySignals.none());
    assertThat(innerPrevious.contains(PROCESS, SPAN)).isTrue();
    assertThat(RabbitMqConsumerProcessTracing.isSuppressed(PROCESS, SPAN)).isTrue();
    assertThat(RabbitMqConsumerProcessTracing.isSuppressed(RECEIVE, CONSUMED_MESSAGES)).isTrue();

    RabbitMqConsumerProcessTracing.restore(innerPrevious);
    assertThat(RabbitMqConsumerProcessTracing.isSuppressed(PROCESS, SPAN)).isTrue();
    assertThat(RabbitMqConsumerProcessTracing.isSuppressed(RECEIVE, CONSUMED_MESSAGES)).isFalse();

    RabbitMqConsumerProcessTracing.restore(outerPrevious);
    assertThat(RabbitMqConsumerProcessTracing.isSuppressed(PROCESS, SPAN)).isFalse();
  }
}
