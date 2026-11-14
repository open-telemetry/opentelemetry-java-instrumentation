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
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqReceiveBatchMessageAttributeExtractor
    implements AttributesExtractor<RocketMqReceiveRequest, List<MessageView>> {

  // copied from semconv
  private static final AttributeKey<Long> ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP =
      AttributeKey.longKey("rocketmq.message.delivery_timestamp");
  private static final AttributeKey<String> ROCKETMQ_MESSAGE_GROUP =
      AttributeKey.stringKey("rocketmq.message.group");
  private static final AttributeKey<List<String>> ROCKETMQ_MESSAGE_KEYS =
      AttributeKey.stringArrayKey("rocketmq.message.keys");
  private static final AttributeKey<String> ROCKETMQ_MESSAGE_TAG =
      AttributeKey.stringKey("rocketmq.message.tag");

  private final boolean captureExperimentalSpanAttributes;

  RocketMqReceiveBatchMessageAttributeExtractor(boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, RocketMqReceiveRequest request) {
    if (captureExperimentalSpanAttributes) {
      attributes.put(ROCKETMQ_MESSAGE_LITE_TOPIC, request.getMessageLiteTopic());
      attributes.put(ROCKETMQ_MESSAGE_PRIORITY, request.getMessagePriority());
    }
    attributes.put(ROCKETMQ_MESSAGE_TAG, request.getMessageTag());
    attributes.put(ROCKETMQ_MESSAGE_GROUP, request.getMessageGroup());
    attributes.put(ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, request.getMessageDeliveryTimestamp());
    attributes.put(ROCKETMQ_MESSAGE_KEYS, request.getMessageKeys());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      RocketMqReceiveRequest request,
      @Nullable List<MessageView> messageViews,
      @Nullable Throwable error) {}
}
