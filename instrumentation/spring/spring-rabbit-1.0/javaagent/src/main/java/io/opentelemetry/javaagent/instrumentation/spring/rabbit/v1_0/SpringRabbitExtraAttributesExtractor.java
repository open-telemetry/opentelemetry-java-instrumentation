/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;

class SpringRabbitExtraAttributesExtractor implements AttributesExtractor<Message, Void> {

  private static final AttributeKey<Long> MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG =
      longKey("messaging.rabbitmq.message.delivery_tag");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Message message) {
    String routingKey = message.getMessageProperties().getReceivedRoutingKey();
    if (routingKey != null && !routingKey.isEmpty()) {
      attributes.put(MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY, routingKey);
    }
    if (emitStableMessagingSemconv()) {
      attributes.put(
          MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG, message.getMessageProperties().getDeliveryTag());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Message message,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
