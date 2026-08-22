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

  private static final VirtualField<BlockingQueueConsumer, Boolean> springListenerConsumer =
      VirtualField.find(BlockingQueueConsumer.class, Boolean.class);
  private static final VirtualField<SimpleMessageListenerContainer, Boolean> consumerBatchEnabled =
      VirtualField.find(SimpleMessageListenerContainer.class, Boolean.class);

  public static boolean hasSingleMessageDelivery(AbstractMessageListenerContainer container) {
    return container.getMessageListener() != null
        && (!(container instanceof SimpleMessageListenerContainer)
            || !Boolean.TRUE.equals(
                consumerBatchEnabled.get((SimpleMessageListenerContainer) container)));
  }

  public static void setConsumerBatchEnabled(
      SimpleMessageListenerContainer container, boolean enabled) {
    consumerBatchEnabled.set(container, enabled);
  }

  public static void markSpringListenerConsumer(BlockingQueueConsumer consumer) {
    springListenerConsumer.set(consumer, Boolean.TRUE);
  }

  public static boolean isSpringListenerConsumer(BlockingQueueConsumer consumer) {
    return Boolean.TRUE.equals(springListenerConsumer.get(consumer));
  }

  private SpringRabbitListenerUtil() {}
}
