/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;

final class RocketMqBatchProcessAttributeExtractor
    implements AttributesExtractor<RocketMqConsumerRequest, ConsumeMessageContext> {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_MESSAGE_TAG =
      AttributeKey.stringKey("messaging.rocketmq.message.tag");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, RocketMqConsumerRequest request) {
    attributes.put(MESSAGING_ROCKETMQ_MESSAGE_TAG, request.getMessageTag());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      RocketMqConsumerRequest request,
      @Nullable ConsumeMessageContext response,
      @Nullable Throwable error) {}
}
