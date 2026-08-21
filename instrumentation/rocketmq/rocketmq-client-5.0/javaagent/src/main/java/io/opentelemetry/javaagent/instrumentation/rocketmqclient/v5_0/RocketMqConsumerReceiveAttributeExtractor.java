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

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqConsumerReceiveAttributeExtractor
    implements AttributesExtractor<RocketMqReceiveRequest, List<MessageView>> {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, RocketMqReceiveRequest request) {
    String consumerGroup = request.getConsumerGroup();
    if (emitStableMessagingSemconv()) {
      attributes.put(MESSAGING_CONSUMER_GROUP_NAME, consumerGroup);
      attributes.put(MESSAGING_ROCKETMQ_NAMESPACE, request.getNamespace());
    }
    if (emitOldMessagingSemconv()) {
      attributes.put(MESSAGING_ROCKETMQ_CLIENT_GROUP, consumerGroup);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      RocketMqReceiveRequest request,
      @Nullable List<MessageView> messageViews,
      @Nullable Throwable error) {}
}
