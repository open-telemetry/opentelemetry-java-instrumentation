/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class RabbitReceiveExtraAttributesExtractor
    implements AttributesExtractor<ReceiveRequest, GetResponse> {

  private static final AttributeKey<Long> MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG =
      longKey("messaging.rabbitmq.message.delivery_tag");

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
      attributes.put(MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY, routingKey);
    }
    if (emitStableMessagingSemconv()) {
      attributes.put(
          MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG, response.getEnvelope().getDeliveryTag());
    }
  }
}
