/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class RabbitChannelSettleAttributesExtractor
    implements AttributesExtractor<ChannelAndMethod, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ChannelAndMethod channelAndMethod) {
    attributes.put(MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG, channelAndMethod.getDeliveryTag());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ChannelAndMethod channelAndMethod,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
