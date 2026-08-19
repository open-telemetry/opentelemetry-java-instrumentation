/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqReceiveBatchMessageAttributeExtractor
    implements AttributesExtractor<RocketMqReceiveRequest, List<MessageView>> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, RocketMqReceiveRequest request) {
    attributes.put(MESSAGING_ROCKETMQ_MESSAGE_TAG, request.getMessageTag());
    attributes.put(MESSAGING_ROCKETMQ_MESSAGE_GROUP, request.getMessageGroup());
    attributes.put(
        MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, request.getMessageDeliveryTimestamp());
    attributes.put(MESSAGING_ROCKETMQ_MESSAGE_KEYS, request.getMessageKeys());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      RocketMqReceiveRequest request,
      @Nullable List<MessageView> messageViews,
      @Nullable Throwable error) {}
}
