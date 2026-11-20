/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_1;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;

class SpringRabbitExtraAttributesExtractor
    implements AttributesExtractor<SpringRabbitRequest, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, SpringRabbitRequest request) {
    Message message = request.getMessage();
    String routingKey = message.getMessageProperties().getReceivedRoutingKey();
    if (routingKey != null && !routingKey.isEmpty()) {
      attributes.put(stringKey("rabbitmq.destination.routing_key"), routingKey);
    }
    attributes.put(
        longKey("rabbitmq.message.delivery_tag"), message.getMessageProperties().getDeliveryTag());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SpringRabbitRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
