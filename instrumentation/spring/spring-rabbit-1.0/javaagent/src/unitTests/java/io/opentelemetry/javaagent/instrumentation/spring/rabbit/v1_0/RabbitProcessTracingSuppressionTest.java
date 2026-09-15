/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.shouldTraceProcess;
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

    boolean installed =
        BlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onEnter(eligibleConsumer);
    try {
      assertThat(installed).isTrue();
      assertThat(shouldTraceProcess()).isFalse();

      boolean nestedEligible =
          LegacyBlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onEnter(
              eligibleConsumer);
      try {
        assertThat(nestedEligible).isFalse();
        assertThat(shouldTraceProcess()).isFalse();
      } finally {
        LegacyBlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onExit(
            nestedEligible);
      }

      boolean nestedIneligible =
          BlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onEnter(
              mock(BlockingQueueConsumer.class));
      try {
        assertThat(nestedIneligible).isFalse();
        assertThat(shouldTraceProcess()).isFalse();
      } finally {
        BlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onExit(nestedIneligible);
      }
    } finally {
      BlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onExit(installed);
    }

    assertThat(shouldTraceProcess()).isTrue();
  }

  @Test
  void directContainerAdviceOwnsSuppression() {
    SimpleMessageListenerContainer eligibleContainer = new SimpleMessageListenerContainer();
    eligibleContainer.setMessageListener((MessageListener) message -> {});
    SimpleMessageListenerContainer ineligibleContainer = new SimpleMessageListenerContainer();

    boolean installed =
        DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onEnter(eligibleContainer);
    try {
      assertThat(installed).isTrue();
      assertThat(shouldTraceProcess()).isFalse();

      boolean nestedEligible =
          DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onEnter(eligibleContainer);
      DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onExit(nestedEligible);
      assertThat(nestedEligible).isFalse();
      assertThat(shouldTraceProcess()).isFalse();

      boolean nestedIneligible =
          DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onEnter(ineligibleContainer);
      DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onExit(nestedIneligible);
      assertThat(nestedIneligible).isFalse();
      assertThat(shouldTraceProcess()).isFalse();
    } finally {
      DirectMessageListenerContainerInstrumentation.ConsumeAdvice.onExit(installed);
    }

    assertThat(shouldTraceProcess()).isTrue();
  }

  @Test
  void owningAdviceCleansUpAfterException() {
    BlockingQueueConsumer consumer = mock(BlockingQueueConsumer.class);
    SpringRabbitListenerUtil.markSpringListenerConsumer(consumer);

    assertThatThrownBy(
            () -> {
              boolean installed =
                  LegacyBlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onEnter(
                      consumer);
              try {
                throw new IllegalStateException("test");
              } finally {
                LegacyBlockingQueueConsumerInstrumentation.ConsumerRegistrationAdvice.onExit(
                    installed);
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(shouldTraceProcess()).isTrue();
  }
}
