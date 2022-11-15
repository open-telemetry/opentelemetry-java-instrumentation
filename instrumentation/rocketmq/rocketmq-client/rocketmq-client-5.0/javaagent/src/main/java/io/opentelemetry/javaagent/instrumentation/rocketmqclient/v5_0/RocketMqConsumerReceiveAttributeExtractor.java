/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MESSAGING_ROCKETMQ_CLIENT_GROUP;

import apache.rocketmq.v2.ReceiveMessageRequest;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

enum RocketMqConsumerReceiveAttributeExtractor
    implements AttributesExtractor<ReceiveMessageRequest, List<MessageView>> {
  INSTANCE;

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ReceiveMessageRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ReceiveMessageRequest request,
      @Nullable List<MessageView> messageViews,
      @Nullable Throwable error) {
    String consumerGroup = request.getGroup().getName();
    attributes.put(MESSAGING_ROCKETMQ_CLIENT_GROUP, consumerGroup);
  }
}
