/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import java.util.Objects;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqBatchProcessSpanLinksExtractor
    implements SpanLinksExtractor<RocketMqConsumerRequest> {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      AttributeKey.stringKey("messaging.message.id");
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_MESSAGE_TAG =
      AttributeKey.stringKey("messaging.rocketmq.message.tag");

  private final TextMapPropagator propagator;
  private final MessageExtractAdapter getter = new MessageExtractAdapter();
  private final boolean captureMessageTag;

  RocketMqBatchProcessSpanLinksExtractor(TextMapPropagator propagator, boolean captureMessageTag) {
    this.propagator = propagator;
    this.captureMessageTag = captureMessageTag;
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, RocketMqConsumerRequest request) {
    for (MessageExt message : request.getMessages()) {
      Context extracted = propagator.extract(Context.root(), message, getter);
      AttributesBuilder attributes = Attributes.builder();
      if (!Objects.equals(message.getTopic(), request.getDestination())) {
        attributes.put(MESSAGING_DESTINATION_NAME, message.getTopic());
      }
      if (!Objects.equals(message.getMsgId(), request.getMessageId())) {
        attributes.put(MESSAGING_MESSAGE_ID, message.getMsgId());
      }
      if (captureMessageTag && !Objects.equals(message.getTags(), request.getMessageTag())) {
        attributes.put(MESSAGING_ROCKETMQ_MESSAGE_TAG, message.getTags());
      }
      spanLinks.addLink(Span.fromContext(extracted).getSpanContext(), attributes.build());
    }
  }
}
