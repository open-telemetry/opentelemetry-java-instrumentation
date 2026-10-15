/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class RabbitChannelExtraAttributesExtractor implements AttributesExtractor<ChannelAndMethod, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ChannelAndMethod channelAndMethod) {
    String routingKey = channelAndMethod.getRoutingKey();
    if (routingKey != null && !routingKey.isEmpty()) {
      attributes.put(MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY, routingKey);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ChannelAndMethod channelAndMethod,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
