/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

public class SpringRabbitListenerUtil {

  private static final VirtualField<BlockingQueueConsumer, Boolean> SPRING_LISTENER_CONSUMER =
      VirtualField.find(BlockingQueueConsumer.class, Boolean.class);
  private static final VirtualField<SimpleMessageListenerContainer, Boolean>
      CONSUMER_BATCH_ENABLED =
          VirtualField.find(SimpleMessageListenerContainer.class, Boolean.class);

  public static boolean shouldTraceListenerProcess(AbstractMessageListenerContainer container) {
    return container.getMessageListener() != null
        && (!(container instanceof SimpleMessageListenerContainer)
            || !Boolean.TRUE.equals(
                CONSUMER_BATCH_ENABLED.get((SimpleMessageListenerContainer) container)));
  }

  public static void setConsumerBatchEnabled(
      SimpleMessageListenerContainer container, boolean enabled) {
    CONSUMER_BATCH_ENABLED.set(container, enabled);
  }

  public static void markSpringListenerConsumer(BlockingQueueConsumer consumer) {
    SPRING_LISTENER_CONSUMER.set(consumer, Boolean.TRUE);
  }

  public static boolean isSpringListenerConsumer(BlockingQueueConsumer consumer) {
    return Boolean.TRUE.equals(SPRING_LISTENER_CONSUMER.get(consumer));
  }

  private SpringRabbitListenerUtil() {}
}
