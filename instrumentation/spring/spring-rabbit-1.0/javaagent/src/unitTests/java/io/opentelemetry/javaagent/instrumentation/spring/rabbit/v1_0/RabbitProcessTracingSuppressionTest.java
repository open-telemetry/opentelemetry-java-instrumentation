/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.processSpanSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

class RabbitProcessTracingSuppressionTest {

  @Test
  void blockingQueueConsumerAdviceOwnsSuppression() {
    BlockingQueueConsumer eligibleConsumer = mock(BlockingQueueConsumer.class);
    SpringRabbitListenerUtil.markSpringListenerConsumer(eligibleConsumer);

    boolean suppressionAcquired =
        BlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onEnter(eligibleConsumer);
    try {
      assertThat(suppressionAcquired).isTrue();
      assertThat(processSpanSuppression().isActive()).isTrue();

      boolean nestedSuppressionAcquired =
          LegacyBlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onEnter(
              eligibleConsumer);
      try {
        assertThat(nestedSuppressionAcquired).isFalse();
        assertThat(processSpanSuppression().isActive()).isTrue();
      } finally {
        LegacyBlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onExit(
            nestedSuppressionAcquired);
      }

      boolean ineligibleSuppressionAcquired =
          BlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onEnter(
              mock(BlockingQueueConsumer.class));
      try {
        assertThat(ineligibleSuppressionAcquired).isFalse();
        assertThat(processSpanSuppression().isActive()).isTrue();
      } finally {
        BlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onExit(
            ineligibleSuppressionAcquired);
      }
    } finally {
      BlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onExit(suppressionAcquired);
    }

    assertThat(processSpanSuppression().isActive()).isFalse();
  }

  @Test
  void directContainerAdviceOwnsSuppression() {
    SimpleMessageListenerContainer eligibleContainer = new SimpleMessageListenerContainer();
    eligibleContainer.setMessageListener((MessageListener) message -> {});
    SimpleMessageListenerContainer ineligibleContainer = new SimpleMessageListenerContainer();

    boolean suppressionAcquired =
        DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onEnter(eligibleContainer);
    try {
      assertThat(suppressionAcquired).isTrue();
      assertThat(processSpanSuppression().isActive()).isTrue();

      boolean nestedSuppressionAcquired =
          DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onEnter(eligibleContainer);
      DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onExit(nestedSuppressionAcquired);
      assertThat(nestedSuppressionAcquired).isFalse();
      assertThat(processSpanSuppression().isActive()).isTrue();

      boolean ineligibleSuppressionAcquired =
          DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onEnter(ineligibleContainer);
      DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onExit(
          ineligibleSuppressionAcquired);
      assertThat(ineligibleSuppressionAcquired).isFalse();
      assertThat(processSpanSuppression().isActive()).isTrue();
    } finally {
      DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onExit(suppressionAcquired);
    }

    assertThat(processSpanSuppression().isActive()).isFalse();
  }

  @Test
  void owningAdviceCleansUpAfterException() {
    BlockingQueueConsumer consumer = mock(BlockingQueueConsumer.class);
    SpringRabbitListenerUtil.markSpringListenerConsumer(consumer);

    assertThatThrownBy(
            () -> {
              boolean suppressionAcquired =
                  LegacyBlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onEnter(
                      consumer);
              try {
                assertThat(suppressionAcquired).isTrue();
                throw new IllegalStateException("test");
              } finally {
                LegacyBlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onExit(
                    suppressionAcquired);
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(processSpanSuppression().isActive()).isFalse();
  }
}
