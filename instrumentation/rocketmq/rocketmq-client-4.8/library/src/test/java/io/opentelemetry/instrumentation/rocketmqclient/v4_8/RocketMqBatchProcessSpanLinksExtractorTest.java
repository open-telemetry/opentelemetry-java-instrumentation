/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import java.util.List;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("deprecation") // using deprecated semconv
class RocketMqBatchProcessSpanLinksExtractorTest {

  private static final String TRACEPARENT =
      "00-00000000000000000000000000000001-0000000000000001-01";

  @Test
  void keepsCommonAttributesOnBatchSpan() {
    assumeTrue(emitStableMessagingSemconv());
    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(
            asList(
                message("topic", "message-1", "tag", TRACEPARENT),
                message("topic", "message-2", "tag", TRACEPARENT)),
            "consumer-group",
            2,
            null);

    RocketMqConsumerAttributeGetter getter = new RocketMqConsumerAttributeGetter();
    assertThat(getter.getDestination(request)).isEqualTo("topic");
    assertThat(getter.getMessageId(request, null)).isNull();
    assertThat(batchAttributes(request).asMap())
        .containsOnly(entry(MESSAGING_ROCKETMQ_MESSAGE_TAG, "tag"));

    assertThat(linkAttributes(request))
        .extracting(Attributes::asMap)
        .containsExactly(
            singletonMap(MESSAGING_MESSAGE_ID, "message-1"),
            singletonMap(MESSAGING_MESSAGE_ID, "message-2"));
  }

  @Test
  void movesDifferentAttributesToMessageLinks() {
    assumeTrue(emitStableMessagingSemconv());
    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(
            asList(
                message("topic-1", "message-1", "tag-1", TRACEPARENT),
                message("topic-2", "message-2", "tag-2", TRACEPARENT)),
            "consumer-group",
            2,
            null);

    RocketMqConsumerAttributeGetter getter = new RocketMqConsumerAttributeGetter();
    assertThat(getter.getDestination(request)).isNull();
    assertThat(getter.getMessageId(request, null)).isNull();
    assertThat(batchAttributes(request)).isEqualTo(Attributes.empty());

    List<Attributes> linkAttributes = linkAttributes(request);
    assertThat(linkAttributes.get(0).asMap())
        .containsOnly(
            entry(MESSAGING_DESTINATION_NAME, "topic-1"),
            entry(MESSAGING_MESSAGE_ID, "message-1"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_TAG, "tag-1"));
    assertThat(linkAttributes.get(1).asMap())
        .containsOnly(
            entry(MESSAGING_DESTINATION_NAME, "topic-2"),
            entry(MESSAGING_MESSAGE_ID, "message-2"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_TAG, "tag-2"));
  }

  private static MessageExt message(
      String topic, String messageId, String tag, String traceparent) {
    MessageExt message = mock(MessageExt.class);
    when(message.getTopic()).thenReturn(topic);
    when(message.getMsgId()).thenReturn(messageId);
    when(message.getTags()).thenReturn(tag);
    when(message.getProperties()).thenReturn(singletonMap("traceparent", traceparent));
    return message;
  }

  private static Attributes batchAttributes(RocketMqConsumerRequest request) {
    AttributesBuilder attributes = Attributes.builder();
    new RocketMqBatchProcessAttributeExtractor().onStart(attributes, Context.root(), request);
    return attributes.build();
  }

  private static List<Attributes> linkAttributes(RocketMqConsumerRequest request) {
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);
    new RocketMqBatchProcessSpanLinksExtractor(W3CTraceContextPropagator.getInstance(), true)
        .extract(spanLinks, Context.root(), request);

    ArgumentCaptor<Attributes> attributes = ArgumentCaptor.forClass(Attributes.class);
    verify(spanLinks, times(2)).addLink(any(SpanContext.class), attributes.capture());
    return attributes.getAllValues();
  }
}
