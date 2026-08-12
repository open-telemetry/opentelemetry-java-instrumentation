/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.util.ArrayList;
import java.util.List;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Messages;
import org.junit.jupiter.api.Test;

class PulsarBatchRequestSpanLinksExtractorTest {

  private static final String TRACE_ID = TraceId.fromLongs(0, 123);
  private static final String SPAN_ID_1 = SpanId.fromLong(456);
  private static final String SPAN_ID_2 = SpanId.fromLong(789);

  private final PulsarBatchRequestSpanLinksExtractor extractor =
      new PulsarBatchRequestSpanLinksExtractor(W3CTraceContextPropagator.getInstance());

  @Test
  void addsOnlyPerMessageAttributesWhenBatchAttributesAgree() {
    String topic = "persistent://public/default/test";
    String partitionTopic = topic + "-partition-0";
    Message<?> message1 = message(partitionTopic, "message-1", SPAN_ID_1);
    Message<?> message2 = message(partitionTopic, "message-2", SPAN_ID_2);
    PulsarBatchRequest request = request(message1, message2);
    RecordingSpanLinksBuilder spanLinks = new RecordingSpanLinksBuilder();

    extractor.extract(spanLinks, Context.root(), request);

    Attributes batchAttributes = batchSpanAttributes(request);
    assertThat(batchAttributes.get(MESSAGING_DESTINATION_NAME))
        .isEqualTo(emitStableMessagingSemconv() ? topic : partitionTopic);
    assertThat(batchAttributes.get(MESSAGING_DESTINATION_PARTITION_ID)).isEqualTo("0");
    assertThat(spanLinks.links)
        .containsExactly(
            linkData(SPAN_ID_1, linkAttributes("message-1").build()),
            linkData(SPAN_ID_2, linkAttributes("message-2").build()));
  }

  @Test
  void keepsSharedDestinationOnBatchWhenPartitionsDiffer() {
    String topic = "persistent://public/default/test";
    Message<?> message1 = message(topic + "-partition-0", "message-1", SPAN_ID_1);
    Message<?> message2 = message(topic + "-partition-1", "message-2", SPAN_ID_2);
    PulsarBatchRequest request = request(message1, message2);
    RecordingSpanLinksBuilder spanLinks = new RecordingSpanLinksBuilder();

    extractor.extract(spanLinks, Context.root(), request);

    Attributes batchAttributes = batchSpanAttributes(request);
    assertThat(batchAttributes.get(MESSAGING_DESTINATION_NAME)).isEqualTo(topic);
    assertThat(batchAttributes.get(MESSAGING_DESTINATION_PARTITION_ID)).isNull();
    assertThat(spanLinks.links)
        .containsExactly(
            linkData(
                SPAN_ID_1,
                linkAttributes("message-1").put(MESSAGING_DESTINATION_PARTITION_ID, "0").build()),
            linkData(
                SPAN_ID_2,
                linkAttributes("message-2").put(MESSAGING_DESTINATION_PARTITION_ID, "1").build()));
  }

  @Test
  void addsEveryDestinationWhenBatchSpansMultipleTopics() {
    String topic1 = "persistent://public/default/topic-1";
    String topic2 = "persistent://public/default/topic-2";
    Message<?> message1 = message(topic1, "message-1", SPAN_ID_1);
    Message<?> message2 = message(topic2, "message-2", SPAN_ID_2);
    PulsarBatchRequest request = request(message1, message2);
    RecordingSpanLinksBuilder spanLinks = new RecordingSpanLinksBuilder();

    extractor.extract(spanLinks, Context.root(), request);

    assertThat(batchSpanAttributes(request).get(MESSAGING_DESTINATION_NAME))
        .isEqualTo(emitStableMessagingSemconv() ? null : topic1);
    assertThat(spanLinks.links)
        .containsExactly(
            linkData(
                SPAN_ID_1,
                linkAttributes("message-1").put(MESSAGING_DESTINATION_NAME, topic1).build()),
            linkData(
                SPAN_ID_2,
                linkAttributes("message-2").put(MESSAGING_DESTINATION_NAME, topic2).build()));
  }

  @Test
  void movesSharedPartitionToLinksWhenDestinationsDiffer() {
    String topic1 = "persistent://public/default/topic-1";
    String topic2 = "persistent://public/default/topic-2";
    Message<?> message1 = message(topic1 + "-partition-0", "message-1", SPAN_ID_1);
    Message<?> message2 = message(topic2 + "-partition-0", "message-2", SPAN_ID_2);
    PulsarBatchRequest request = request(message1, message2);
    RecordingSpanLinksBuilder spanLinks = new RecordingSpanLinksBuilder();

    extractor.extract(spanLinks, Context.root(), request);

    // a partition id is only unique within a destination name, so it is not recorded on the batch
    // span when the destination name is not recorded there either
    Attributes batchAttributes = batchSpanAttributes(request);
    assertThat(batchAttributes.get(MESSAGING_DESTINATION_NAME))
        .isEqualTo(emitStableMessagingSemconv() ? null : topic1);
    assertThat(batchAttributes.get(MESSAGING_DESTINATION_PARTITION_ID)).isNull();
    assertThat(spanLinks.links)
        .containsExactly(
            linkData(
                SPAN_ID_1,
                linkAttributes("message-1")
                    .put(MESSAGING_DESTINATION_NAME, topic1)
                    .put(MESSAGING_DESTINATION_PARTITION_ID, "0")
                    .build()),
            linkData(
                SPAN_ID_2,
                linkAttributes("message-2")
                    .put(MESSAGING_DESTINATION_NAME, topic2)
                    .put(MESSAGING_DESTINATION_PARTITION_ID, "0")
                    .build()));
  }

  private static Message<?> message(String topic, String messageId, String spanId) {
    Message<?> message = mock(Message.class);
    MessageId id = mock(MessageId.class);
    when(id.toString()).thenReturn(messageId);
    when(message.getMessageId()).thenReturn(id);
    when(message.getTopicName()).thenReturn(topic);
    when(message.getProperties())
        .thenReturn(singletonMap("traceparent", String.format("00-%s-%s-01", TRACE_ID, spanId)));
    return message;
  }

  private static PulsarBatchRequest request(Message<?>... messages) {
    List<Message<?>> messageList = asList(messages);
    @SuppressWarnings("unchecked")
    Messages<Object> batch = mock(Messages.class);
    when(batch.iterator()).thenAnswer(invocation -> messageList.iterator());
    when(batch.size()).thenReturn(messageList.size());
    @SuppressWarnings("unchecked")
    Consumer<Object> consumer = mock(Consumer.class);
    when(consumer.getSubscription()).thenReturn("subscription");
    return PulsarBatchRequest.create(batch, null, consumer);
  }

  private static AttributesBuilder linkAttributes(String messageId) {
    return Attributes.builder().put(MESSAGING_MESSAGE_ID, messageId);
  }

  private static LinkData linkData(String spanId, Attributes stableAttributes) {
    SpanContext spanContext =
        SpanContext.createFromRemoteParent(
            TRACE_ID, spanId, TraceFlags.getSampled(), TraceState.getDefault());
    return LinkData.create(
        spanContext, emitStableMessagingSemconv() ? stableAttributes : Attributes.empty());
  }

  private static Attributes batchSpanAttributes(PulsarBatchRequest request) {
    AttributesBuilder attributes = Attributes.builder();
    MessagingAttributesExtractor.<PulsarBatchRequest, Void>create(
            new PulsarBatchMessagingAttributesGetter(), MessagingOperationType.RECEIVE, "receive")
        .onStart(attributes, Context.root(), request);
    return attributes.build();
  }

  private static final class RecordingSpanLinksBuilder implements SpanLinksBuilder {
    private final List<LinkData> links = new ArrayList<>();

    @Override
    public SpanLinksBuilder addLink(SpanContext spanContext) {
      links.add(LinkData.create(spanContext));
      return this;
    }

    @Override
    public SpanLinksBuilder addLink(SpanContext spanContext, Attributes attributes) {
      links.add(LinkData.create(spanContext, attributes));
      return this;
    }
  }
}
