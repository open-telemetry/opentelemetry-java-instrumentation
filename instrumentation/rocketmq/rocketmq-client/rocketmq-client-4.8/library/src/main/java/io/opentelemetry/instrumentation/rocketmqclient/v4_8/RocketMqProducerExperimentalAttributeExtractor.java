/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;

enum RocketMqProducerExperimentalAttributeExtractor
    implements AttributesExtractor<SendMessageContext, Void> {
  INSTANCE;

  private static final AttributeKey<String> MESSAGING_ROCKETMQ_BROKER_ADDRESS =
      AttributeKey.stringKey("messaging.rocketmq.broker_address");
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_SEND_RESULT =
      AttributeKey.stringKey("messaging.rocketmq.send_result");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, SendMessageContext request) {
    if (request.getMessage() != null) {
      String tags = request.getMessage().getTags();
      if (tags != null) {
        attributes.put(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, tags);
      }
    }
    String brokerAddr = request.getBrokerAddr();
    if (brokerAddr != null) {
      attributes.put(MESSAGING_ROCKETMQ_BROKER_ADDRESS, brokerAddr);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SendMessageContext request,
      @Nullable Void unused,
      @Nullable Throwable error) {
    if (request.getSendResult() != null) {
      attributes.put(
          MESSAGING_ROCKETMQ_SEND_RESULT, request.getSendResult().getSendStatus().name());
    }
  }
}
