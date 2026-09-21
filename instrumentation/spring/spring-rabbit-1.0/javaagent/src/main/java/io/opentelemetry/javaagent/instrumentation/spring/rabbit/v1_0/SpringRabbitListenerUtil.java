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

  private static final VirtualField<BlockingQueueConsumer, Boolean>
      BLOCKING_QUEUE_LISTENER_PROCESSING_SELECTED =
          VirtualField.find(BlockingQueueConsumer.class, Boolean.class);
  // RabbitMQ copies this selection into its immutable wrapper for each basicConsume registration.
  private static final VirtualField<Consumer, Boolean> LISTENER_PROCESSING_SELECTED =
      VirtualField.find(Consumer.class, Boolean.class);
  private static final VirtualField<SimpleMessageListenerContainer, Boolean>
      CONSUMER_BATCH_ENABLED =
          VirtualField.find(SimpleMessageListenerContainer.class, Boolean.class);

  public static boolean isListenerProcessingSelected(AbstractMessageListenerContainer container) {
    return container.getMessageListener() != null
        && (!(container instanceof SimpleMessageListenerContainer)
            || !Boolean.TRUE.equals(
                CONSUMER_BATCH_ENABLED.get((SimpleMessageListenerContainer) container)));
  }

  public static boolean isListenerProcessingSelected(BlockingQueueConsumer consumer) {
    return Boolean.TRUE.equals(BLOCKING_QUEUE_LISTENER_PROCESSING_SELECTED.get(consumer));
  }

  public static void setConsumerBatchEnabled(
      SimpleMessageListenerContainer container, boolean enabled) {
    CONSUMER_BATCH_ENABLED.set(container, enabled);
  }

  public static void selectListenerProcessing(BlockingQueueConsumer consumer) {
    BLOCKING_QUEUE_LISTENER_PROCESSING_SELECTED.set(consumer, Boolean.TRUE);
  }

  public static void setListenerProcessingSelected(
      Consumer consumer, boolean listenerProcessingSelected) {
    LISTENER_PROCESSING_SELECTED.set(consumer, listenerProcessingSelected);
  }

  private SpringRabbitListenerUtil() {}
}
