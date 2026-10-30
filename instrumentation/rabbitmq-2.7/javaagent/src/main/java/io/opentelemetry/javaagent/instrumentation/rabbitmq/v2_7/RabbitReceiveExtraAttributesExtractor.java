/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class RabbitReceiveExtraAttributesExtractor
    implements AttributesExtractor<ReceiveRequest, GetResponse> {
  // copied from semconv
  private static final AttributeKey<String> RABBITMQ_DESTINATION_ROUTING_KEY =
      AttributeKey.stringKey("rabbitmq.destination.routing_key");
  private static final AttributeKey<Long> RABBITMQ_MESSAGE_DELIVERY_TAG =
      AttributeKey.longKey("rabbitmq.message.delivery_tag");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ReceiveRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ReceiveRequest request,
      @Nullable GetResponse response,
      @Nullable Throwable error) {
    if (response == null) {
      response = request.getResponse();
      if (response == null) {
        return;
      }
    }
    String routingKey = response.getEnvelope().getRoutingKey();
    if (routingKey != null && !routingKey.isEmpty()) {
      attributes.put(RABBITMQ_DESTINATION_ROUTING_KEY, routingKey);
    }
    attributes.put(RABBITMQ_MESSAGE_DELIVERY_TAG, response.getEnvelope().getDeliveryTag());
  }
}
