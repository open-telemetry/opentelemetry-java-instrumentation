/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_NAMESPACE;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqConsumerReceiveAttributeExtractor
    implements AttributesExtractor<RocketMqReceiveRequest, List<MessageView>> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, RocketMqReceiveRequest request) {
    String consumerGroup = request.getConsumerGroup();
    attributes.put(MESSAGING_CONSUMER_GROUP_NAME, consumerGroup);
    attributes.put(MESSAGING_ROCKETMQ_NAMESPACE, request.getNamespace());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      RocketMqReceiveRequest request,
      @Nullable List<MessageView> messageViews,
      @Nullable Throwable error) {}
}
