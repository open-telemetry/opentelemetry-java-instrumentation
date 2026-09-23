/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import com.rabbitmq.client.Consumer;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

public class SpringRabbitListenerUtil {

  private static final VirtualField<BlockingQueueConsumer, Boolean> SPRING_RABBIT_OWNS_PROCESSING =
      VirtualField.find(BlockingQueueConsumer.class, Boolean.class);
  // RabbitMQ reads this value when wrapping the consumer for each basicConsume registration.
  private static final VirtualField<Consumer, Boolean> PROCESSING_OWNED_OUTSIDE_RABBIT_CLIENT =
      VirtualField.find(Consumer.class, Boolean.class);
  private static final VirtualField<SimpleMessageListenerContainer, Boolean>
      CONSUMER_BATCH_ENABLED =
          VirtualField.find(SimpleMessageListenerContainer.class, Boolean.class);

  public static boolean canTraceListenerProcessing(AbstractMessageListenerContainer container) {
    return container.getMessageListener() != null
        && (!(container instanceof SimpleMessageListenerContainer)
            || !Boolean.TRUE.equals(
                CONSUMER_BATCH_ENABLED.get((SimpleMessageListenerContainer) container)));
  }

  public static boolean springRabbitOwnsProcessing(BlockingQueueConsumer consumer) {
    return Boolean.TRUE.equals(SPRING_RABBIT_OWNS_PROCESSING.get(consumer));
  }

  public static void setConsumerBatchEnabled(
      SimpleMessageListenerContainer container, boolean enabled) {
    CONSUMER_BATCH_ENABLED.set(container, enabled);
  }

  public static void markSpringRabbitAsProcessingOwner(BlockingQueueConsumer consumer) {
    SPRING_RABBIT_OWNS_PROCESSING.set(consumer, Boolean.TRUE);
  }

  public static void setProcessingOwnedOutsideRabbitClient(
      Consumer consumer, boolean processingOwnedOutsideRabbitClient) {
    PROCESSING_OWNED_OUTSIDE_RABBIT_CLIENT.set(consumer, processingOwnedOutsideRabbitClient);
  }

  private SpringRabbitListenerUtil() {}
}
