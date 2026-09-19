/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import apache.rocketmq.v2.ReceiveMessageRequest;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import java.util.List;
import java.util.Optional;
import org.apache.rocketmq.client.apis.message.MessageId;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("deprecation") // using deprecated semconv
class RocketMqReceiveSpanLinksExtractorTest {

  private static final String TRACEPARENT =
      "00-00000000000000000000000000000001-0000000000000001-01";

  @Test
  void doesNotInspectMessagesWhenCreatingRequest() {
    ReceiveMessageRequest receiveRequest = mock(ReceiveMessageRequest.class);
    MessageView first = mock(MessageView.class);
    MessageView second = mock(MessageView.class);

    RocketMqReceiveRequest.create(receiveRequest, asList(first, second));

    verifyNoInteractions(receiveRequest, first, second);
  }

  @Test
  void keepsCommonAttributesOnReceiveSpan() {
    assumeTrue(emitStableMessagingSemconv());
    RocketMqReceiveRequest request =
        request(
            message("topic", "message-1", "tag", "group", 123L, "key", TRACEPARENT),
            message("topic", "message-2", "tag", "group", 123L, "key", TRACEPARENT));

    RocketMqConsumerReceiveAttributeGetter getter = new RocketMqConsumerReceiveAttributeGetter();
    assertThat(getter.getDestination(request)).isEqualTo("topic");
    assertThat(getter.getMessageId(request, null)).isNull();
    assertThat(batchAttributes(request).asMap())
        .containsOnly(
            entry(MESSAGING_ROCKETMQ_MESSAGE_TAG, "tag"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_GROUP, "group"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, 123L),
            entry(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList("key")));

    assertThat(linkAttributes(request))
        .extracting(Attributes::asMap)
        .containsExactly(
            singletonMap(MESSAGING_MESSAGE_ID, "message-1"),
            singletonMap(MESSAGING_MESSAGE_ID, "message-2"));
  }

  @Test
  void movesDifferentAttributesToMessageLinks() {
    assumeTrue(emitStableMessagingSemconv());
    RocketMqReceiveRequest request =
        request(
            message("topic-1", "message-1", "tag-1", "group-1", 123L, "key-1", TRACEPARENT),
            message("topic-2", "message-2", "tag-2", "group-2", 456L, "key-2", TRACEPARENT));

    RocketMqConsumerReceiveAttributeGetter getter = new RocketMqConsumerReceiveAttributeGetter();
    assertThat(getter.getDestination(request)).isNull();
    assertThat(getter.getMessageId(request, null)).isNull();
    assertThat(batchAttributes(request)).isEqualTo(Attributes.empty());

    List<Attributes> linkAttributes = linkAttributes(request);
    assertThat(linkAttributes.get(0).asMap())
        .containsOnly(
            entry(MESSAGING_DESTINATION_NAME, "topic-1"),
            entry(MESSAGING_MESSAGE_ID, "message-1"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_TAG, "tag-1"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_GROUP, "group-1"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, 123L),
            entry(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList("key-1")));
    assertThat(linkAttributes.get(1).asMap())
        .containsOnly(
            entry(MESSAGING_DESTINATION_NAME, "topic-2"),
            entry(MESSAGING_MESSAGE_ID, "message-2"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_TAG, "tag-2"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_GROUP, "group-2"),
            entry(MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, 456L),
            entry(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList("key-2")));
  }

  @Test
  void keepsMessageIdOnLinkOfSingleMessageReceive() {
    assumeTrue(emitStableMessagingSemconv());
    RocketMqReceiveRequest request =
        request(message("topic", "message-1", "tag", "group", 123L, "key", TRACEPARENT));

    RocketMqConsumerReceiveAttributeGetter getter = new RocketMqConsumerReceiveAttributeGetter();
    assertThat(getter.getMessageId(request, null)).isNull();

    assertThat(linkAttributes(request, 1))
        .extracting(Attributes::asMap)
        .containsExactly(singletonMap(MESSAGING_MESSAGE_ID, "message-1"));
  }

  private static RocketMqReceiveRequest request(MessageView... messages) {
    return RocketMqReceiveRequest.create(mock(ReceiveMessageRequest.class), asList(messages));
  }

  private static MessageView message(
      String topic,
      String messageIdValue,
      String tag,
      String group,
      long deliveryTimestamp,
      String key,
      String traceparent) {
    MessageId messageId = mock(MessageId.class);
    when(messageId.toString()).thenReturn(messageIdValue);
    MessageView message = mock(MessageView.class);
    when(message.getTopic()).thenReturn(topic);
    when(message.getMessageId()).thenReturn(messageId);
    when(message.getTag()).thenReturn(Optional.of(tag));
    when(message.getMessageGroup()).thenReturn(Optional.of(group));
    when(message.getDeliveryTimestamp()).thenReturn(Optional.of(deliveryTimestamp));
    when(message.getKeys()).thenReturn(singleton(key));
    when(message.getProperties()).thenReturn(singletonMap("traceparent", traceparent));
    return message;
  }

  private static Attributes batchAttributes(RocketMqReceiveRequest request) {
    AttributesBuilder attributes = Attributes.builder();
    new RocketMqReceiveBatchMessageAttributeExtractor()
        .onStart(attributes, Context.root(), request);
    return attributes.build();
  }

  private static List<Attributes> linkAttributes(RocketMqReceiveRequest request) {
    return linkAttributes(request, 2);
  }

  private static List<Attributes> linkAttributes(RocketMqReceiveRequest request, int linkCount) {
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);
    new RocketMqReceiveSpanLinksExtractor(W3CTraceContextPropagator.getInstance())
        .extract(spanLinks, Context.root(), request);

    ArgumentCaptor<Attributes> attributes = ArgumentCaptor.forClass(Attributes.class);
    verify(spanLinks, times(linkCount)).addLink(any(SpanContext.class), attributes.capture());
    return attributes.getAllValues();
  }
}
