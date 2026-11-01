/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqReceiveSpanLinksExtractor implements SpanLinksExtractor<RocketMqReceiveRequest> {
  // copied from semconv
  private static final AttributeKey<Long> ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP =
      AttributeKey.longKey("rocketmq.message.delivery_timestamp");
  private static final AttributeKey<String> ROCKETMQ_MESSAGE_GROUP =
      AttributeKey.stringKey("rocketmq.message.group");
  private static final AttributeKey<List<String>> ROCKETMQ_MESSAGE_KEYS =
      AttributeKey.stringArrayKey("rocketmq.message.keys");
  private static final AttributeKey<String> ROCKETMQ_MESSAGE_TAG =
      AttributeKey.stringKey("rocketmq.message.tag");

  private final TextMapPropagator propagator;
  private final MessageMapGetter getter = new MessageMapGetter();

  RocketMqReceiveSpanLinksExtractor(TextMapPropagator propagator) {
    this.propagator = propagator;
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, RocketMqReceiveRequest request) {
    for (MessageView message : request.getMessages()) {
      Context extracted = propagator.extract(Context.root(), message, getter);
      AttributesBuilder attributes = Attributes.builder();
      if (!Objects.equals(message.getTopic(), request.getDestination())) {
        attributes.put(MESSAGING_DESTINATION_NAME, message.getTopic());
      }
      String messageId = Objects.toString(message.getMessageId(), null);
      attributes.put(MESSAGING_MESSAGE_ID, messageId);
      String messageTag = message.getTag().orElse(null);
      if (!Objects.equals(messageTag, request.getMessageTag())) {
        attributes.put(ROCKETMQ_MESSAGE_TAG, messageTag);
      }
      String messageGroup = message.getMessageGroup().orElse(null);
      if (!Objects.equals(messageGroup, request.getMessageGroup())) {
        attributes.put(ROCKETMQ_MESSAGE_GROUP, messageGroup);
      }
      Long deliveryTimestamp = message.getDeliveryTimestamp().orElse(null);
      if (!Objects.equals(deliveryTimestamp, request.getMessageDeliveryTimestamp())) {
        attributes.put(ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, deliveryTimestamp);
      }
      ArrayList<String> messageKeys = new ArrayList<>(message.getKeys());
      if (request.getMessageKeys() == null
          || !new HashSet<>(messageKeys).equals(new HashSet<>(request.getMessageKeys()))) {
        attributes.put(ROCKETMQ_MESSAGE_KEYS, messageKeys);
      }
      spanLinks.addLink(Span.fromContext(extracted).getSpanContext(), attributes.build());
    }
  }
}
