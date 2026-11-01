/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingRocketmqMessageTypeIncubatingValues;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

class RocketMqProducerAttributeExtractor
    implements AttributesExtractor<PublishingMessageImpl, SendReceiptImpl> {
  // copied from semconv
  private static final AttributeKey<Long> ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP =
      AttributeKey.longKey("rocketmq.message.delivery_timestamp");
  private static final AttributeKey<String> ROCKETMQ_MESSAGE_GROUP =
      AttributeKey.stringKey("rocketmq.message.group");
  private static final AttributeKey<List<String>> ROCKETMQ_MESSAGE_KEYS =
      AttributeKey.stringArrayKey("rocketmq.message.keys");
  private static final AttributeKey<String> ROCKETMQ_MESSAGE_TAG =
      AttributeKey.stringKey("rocketmq.message.tag");
  private static final AttributeKey<String> ROCKETMQ_MESSAGE_TYPE =
      AttributeKey.stringKey("rocketmq.message.type");
  private static final AttributeKey<String> ROCKETMQ_NAMESPACE =
      AttributeKey.stringKey("rocketmq.namespace");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, PublishingMessageImpl message) {
    attributes.put(ROCKETMQ_NAMESPACE, VirtualFieldStore.getNamespaceByMessage(message));
    message.getTag().ifPresent(s -> attributes.put(ROCKETMQ_MESSAGE_TAG, s));
    message.getMessageGroup().ifPresent(s -> attributes.put(ROCKETMQ_MESSAGE_GROUP, s));
    message
        .getDeliveryTimestamp()
        .ifPresent(s -> attributes.put(ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, s));
    attributes.put(ROCKETMQ_MESSAGE_KEYS, new ArrayList<>(message.getKeys()));
    switch (message.getMessageType()) {
      case FIFO:
        attributes.put(ROCKETMQ_MESSAGE_TYPE, MessagingRocketmqMessageTypeIncubatingValues.FIFO);
        break;
      case DELAY:
        attributes.put(ROCKETMQ_MESSAGE_TYPE, MessagingRocketmqMessageTypeIncubatingValues.DELAY);
        break;
      case TRANSACTION:
        attributes.put(
            ROCKETMQ_MESSAGE_TYPE, MessagingRocketmqMessageTypeIncubatingValues.TRANSACTION);
        break;
      default:
        attributes.put(ROCKETMQ_MESSAGE_TYPE, MessagingRocketmqMessageTypeIncubatingValues.NORMAL);
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
