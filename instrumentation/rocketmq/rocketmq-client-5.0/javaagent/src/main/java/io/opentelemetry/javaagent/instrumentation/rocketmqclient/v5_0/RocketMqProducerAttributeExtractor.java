/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0.RocketMqMessageAttributes.ROCKETMQ_MESSAGE_LITE_TOPIC;
import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0.RocketMqMessageAttributes.ROCKETMQ_MESSAGE_PRIORITY;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

  private final boolean captureExperimentalSpanAttributes;

  RocketMqProducerAttributeExtractor(boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

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
    attributes.put(ROCKETMQ_MESSAGE_TYPE, message.getMessageType().name().toLowerCase(Locale.ROOT));
    if (captureExperimentalSpanAttributes) {
      attributes.put(ROCKETMQ_MESSAGE_LITE_TOPIC, RocketMqMessageAttributes.getLiteTopic(message));
      attributes.put(ROCKETMQ_MESSAGE_PRIORITY, RocketMqMessageAttributes.getPriority(message));
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
