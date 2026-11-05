/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.message.MessageViewImpl;

class RocketMqConsumerProcessAttributeExtractor
    implements AttributesExtractor<MessageView, ConsumeResult> {

  // copied from semconv
  private static final AttributeKey<Long> ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP =
      AttributeKey.longKey("rocketmq.message.delivery_timestamp");
  private static final AttributeKey<String> ROCKETMQ_MESSAGE_GROUP =
      AttributeKey.stringKey("rocketmq.message.group");
  private static final AttributeKey<List<String>> ROCKETMQ_MESSAGE_KEYS =
      AttributeKey.stringArrayKey("rocketmq.message.keys");
  private static final AttributeKey<String> ROCKETMQ_MESSAGE_TAG =
      AttributeKey.stringKey("rocketmq.message.tag");
  private static final AttributeKey<String> ROCKETMQ_NAMESPACE =
      AttributeKey.stringKey("rocketmq.namespace");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, MessageView messageView) {
    messageView.getTag().ifPresent(s -> attributes.put(ROCKETMQ_MESSAGE_TAG, s));
    messageView.getMessageGroup().ifPresent(s -> attributes.put(ROCKETMQ_MESSAGE_GROUP, s));
    messageView
        .getDeliveryTimestamp()
        .ifPresent(s -> attributes.put(ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, s));
    attributes.put(ROCKETMQ_MESSAGE_KEYS, new ArrayList<>(messageView.getKeys()));
    String consumerGroup = VirtualFieldStore.getConsumerGroupByMessage(messageView);
    attributes.put(MESSAGING_CONSUMER_GROUP_NAME, consumerGroup);
    String namespace =
        ((MessageViewImpl) messageView).getMessageQueue().getTopicResource().getNamespace();
    attributes.put(ROCKETMQ_NAMESPACE, StringUtils.emptyToNull(namespace));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      MessageView messageView,
      @Nullable ConsumeResult consumeResult,
      @Nullable Throwable error) {}
}
