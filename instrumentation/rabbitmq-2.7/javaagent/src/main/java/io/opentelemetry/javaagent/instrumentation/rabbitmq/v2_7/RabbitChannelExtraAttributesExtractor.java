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

class RabbitChannelExtraAttributesExtractor implements AttributesExtractor<ChannelAndMethod, Void> {
  // copied from semconv
  private static final AttributeKey<String> RABBITMQ_DESTINATION_ROUTING_KEY =
      AttributeKey.stringKey("rabbitmq.destination.routing_key");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ChannelAndMethod channelAndMethod) {
    String routingKey = channelAndMethod.getRoutingKey();
    if (routingKey != null && !routingKey.isEmpty()) {
      attributes.put(RABBITMQ_DESTINATION_ROUTING_KEY, routingKey);
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
