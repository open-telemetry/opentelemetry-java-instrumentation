/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class RabbitChannelSettleAttributesExtractor
    implements AttributesExtractor<ChannelAndMethod, Void> {
  // copied from semconv
  private static final AttributeKey<Long> RABBITMQ_MESSAGE_DELIVERY_TAG =
      AttributeKey.longKey("rabbitmq.message.delivery_tag");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ChannelAndMethod channelAndMethod) {
    attributes.put(RABBITMQ_MESSAGE_DELIVERY_TAG, channelAndMethod.getDeliveryTag());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ChannelAndMethod channelAndMethod,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
