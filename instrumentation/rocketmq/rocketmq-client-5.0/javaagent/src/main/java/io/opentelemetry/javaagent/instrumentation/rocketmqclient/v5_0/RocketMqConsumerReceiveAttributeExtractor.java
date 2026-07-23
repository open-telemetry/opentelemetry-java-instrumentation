/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_CLIENT_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_NAMESPACE;

import apache.rocketmq.v2.ReceiveMessageRequest;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqConsumerReceiveAttributeExtractor
    implements AttributesExtractor<ReceiveMessageRequest, List<MessageView>> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ReceiveMessageRequest request) {}

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ReceiveMessageRequest request,
      @Nullable List<MessageView> messageViews,
      @Nullable Throwable error) {
    String consumerGroup = request.getGroup().getName();
    if (emitStableMessagingSemconv()) {
      attributes.put(MESSAGING_CONSUMER_GROUP_NAME, consumerGroup);
      attributes.put(
          MESSAGING_ROCKETMQ_NAMESPACE,
          request.getMessageQueue().getTopic().getResourceNamespace());
    }
    if (emitOldMessagingSemconv()) {
      attributes.put(MESSAGING_ROCKETMQ_CLIENT_GROUP, consumerGroup);
    }
  }
}
