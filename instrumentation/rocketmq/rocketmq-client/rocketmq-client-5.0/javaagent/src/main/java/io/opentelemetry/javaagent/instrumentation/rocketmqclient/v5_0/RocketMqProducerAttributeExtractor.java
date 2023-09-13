/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_GROUP;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TYPE;
import static io.opentelemetry.semconv.SemanticAttributes.MessagingRocketmqMessageTypeValues.DELAY;
import static io.opentelemetry.semconv.SemanticAttributes.MessagingRocketmqMessageTypeValues.FIFO;
import static io.opentelemetry.semconv.SemanticAttributes.MessagingRocketmqMessageTypeValues.NORMAL;
import static io.opentelemetry.semconv.SemanticAttributes.MessagingRocketmqMessageTypeValues.TRANSACTION;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.ArrayList;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

enum RocketMqProducerAttributeExtractor
    implements AttributesExtractor<PublishingMessageImpl, SendReceiptImpl> {
  INSTANCE;

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, PublishingMessageImpl message) {
    message.getTag().ifPresent(s -> attributes.put(MESSAGING_ROCKETMQ_MESSAGE_TAG, s));
    message.getMessageGroup().ifPresent(s -> attributes.put(MESSAGING_ROCKETMQ_MESSAGE_GROUP, s));
    message
        .getDeliveryTimestamp()
        .ifPresent(s -> attributes.put(MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, s));
    attributes.put(MESSAGING_ROCKETMQ_MESSAGE_KEYS, new ArrayList<>(message.getKeys()));
    switch (message.getMessageType()) {
      case FIFO:
        attributes.put(MESSAGING_ROCKETMQ_MESSAGE_TYPE, FIFO);
        break;
      case DELAY:
        attributes.put(MESSAGING_ROCKETMQ_MESSAGE_TYPE, DELAY);
        break;
      case TRANSACTION:
        attributes.put(MESSAGING_ROCKETMQ_MESSAGE_TYPE, TRANSACTION);
        break;
      default:
        attributes.put(MESSAGING_ROCKETMQ_MESSAGE_TYPE, NORMAL);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      PublishingMessageImpl message,
      @Nullable SendReceiptImpl sendReceipt,
      @Nullable Throwable error) {}
}
