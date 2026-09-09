/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaBatchProcessSpanLinksExtractorTest {

  private static final SpanContext LINK_CONTEXT =
      SpanContext.createFromRemoteParent(
          "00000000000000000000000000000001",
          "0000000000000001",
          TraceFlags.getSampled(),
          TraceState.getDefault());

  @Test
  void keepsCommonPartitionOnBatchSpan() {
    assumeTrue(emitStableMessagingSemconv());
    ConsumerRecord<String, String> first = record("topic", 1, 10, "key");
    ConsumerRecord<String, String> second = record("topic", 1, 10, "key");
    KafkaReceiveRequest request = request(first, second);

    AttributesBuilder spanAttributes = Attributes.builder();
    new KafkaReceiveAttributesExtractor().onStart(spanAttributes, Context.root(), request);
    RecordingSpanLinksBuilder links = extractLinks(request);

    // the destination is emitted by MessagingAttributesExtractor, not by the extractor under test
    assertThat(spanAttributes.build())
        .isEqualTo(Attributes.builder().put(MESSAGING_DESTINATION_PARTITION_ID, "1").build());
    // the offset and the message key stay on the links even though they are the same for every
    // record, because they are only recommended on spans that describe a single message operation
    assertThat(links.attributes)
        .containsExactly(
            linkAttributes(null, null, 10, "key"), linkAttributes(null, null, 10, "key"));
    assertThat(links.linksWithAttributes).isEqualTo(2);
  }

  @Test
  void keepsOffsetAndKeyOnLinkOfSingleRecordBatch() {
    assumeTrue(emitStableMessagingSemconv());
    KafkaReceiveRequest request = request(record("topic", 1, 10, "key"));

    AttributesBuilder spanAttributes = Attributes.builder();
    new KafkaReceiveAttributesExtractor().onStart(spanAttributes, Context.root(), request);
    RecordingSpanLinksBuilder links = extractLinks(request);

    assertThat(spanAttributes.build())
        .isEqualTo(Attributes.builder().put(MESSAGING_DESTINATION_PARTITION_ID, "1").build());
    assertThat(links.attributes).containsExactly(linkAttributes(null, null, 10, "key"));
    assertThat(links.linksWithAttributes).isEqualTo(1);
  }

  @Test
  void movesDifferingValuesToRecordLinks() {
    assumeTrue(emitStableMessagingSemconv());
    ConsumerRecord<String, String> first = record("topic-a", 1, 10, "key-a");
    ConsumerRecord<String, String> second = record("topic-b", 2, 20, "key-b");
    KafkaReceiveRequest request = request(first, second);

    AttributesBuilder spanAttributes = Attributes.builder();
    new KafkaReceiveAttributesExtractor().onStart(spanAttributes, Context.root(), request);
    RecordingSpanLinksBuilder links = extractLinks(request);

    assertThat(spanAttributes.build()).isEqualTo(Attributes.empty());
    assertThat(links.attributes)
        .containsExactly(
            linkAttributes("topic-a", "1", 10, "key-a"),
            linkAttributes("topic-b", "2", 20, "key-b"));
    assertThat(links.linksWithAttributes).isEqualTo(2);
  }

  @Test
  void movesPartitionToRecordLinksWhenDestinationVaries() {
    assumeTrue(emitStableMessagingSemconv());
    ConsumerRecord<String, String> first = record("topic-a", 0, 5, "key");
    ConsumerRecord<String, String> second = record("topic-b", 0, 6, "key");
    KafkaReceiveRequest request = request(first, second);

    AttributesBuilder spanAttributes = Attributes.builder();
    new KafkaReceiveAttributesExtractor().onStart(spanAttributes, Context.root(), request);
    RecordingSpanLinksBuilder links = extractLinks(request);

    // the batch spans two topics, so no destination name is emitted on the batch span, which means
    // the partition id would be orphaned there
    assertThat(new KafkaReceiveAttributesGetter().getDestination(request)).isNull();
    assertThat(spanAttributes.build()).isEqualTo(Attributes.empty());
    assertThat(links.attributes)
        .containsExactly(
            linkAttributes("topic-a", "0", 5, "key"), linkAttributes("topic-b", "0", 6, "key"));
    assertThat(links.linksWithAttributes).isEqualTo(2);
  }

  @Test
  void keepsDestinationOnBatchSpanWhenOnlyPartitionVaries() {
    assumeTrue(emitStableMessagingSemconv());
    ConsumerRecord<String, String> first = record("topic", 0, 5, "key");
    ConsumerRecord<String, String> second = record("topic", 1, 5, "key");
    KafkaReceiveRequest request = request(first, second);

    AttributesBuilder spanAttributes = Attributes.builder();
    new KafkaReceiveAttributesExtractor().onStart(spanAttributes, Context.root(), request);
    RecordingSpanLinksBuilder links = extractLinks(request);

    assertThat(new KafkaReceiveAttributesGetter().getDestination(request)).isEqualTo("topic");
    assertThat(spanAttributes.build()).isEqualTo(Attributes.empty());
    assertThat(links.attributes)
        .containsExactly(linkAttributes(null, "0", 5, "key"), linkAttributes(null, "1", 5, "key"));
    assertThat(links.linksWithAttributes).isEqualTo(2);
  }

  @Test
  void movesPartitionToRecordLinksWhenBatchHasEmptyPartitionOfAnotherTopic() {
    assumeTrue(emitStableMessagingSemconv());
    Map<TopicPartition, List<ConsumerRecord<String, String>>> recordsByPartition =
        new LinkedHashMap<>();
    recordsByPartition.put(
        new TopicPartition("topic-a", 0), singletonList(record("topic-a", 0, 5, "key")));
    recordsByPartition.put(new TopicPartition("topic-b", 0), emptyList());
    KafkaReceiveRequest request =
        KafkaReceiveRequest.create(new ConsumerRecords<>(recordsByPartition), null, null);

    AttributesBuilder spanAttributes = Attributes.builder();
    new KafkaReceiveAttributesExtractor().onStart(spanAttributes, Context.root(), request);
    RecordingSpanLinksBuilder links = extractLinks(request);

    // the empty partition of the second topic still leaves the batch span without a destination
    // name, so the partition id must not stay on it either
    assertThat(new KafkaReceiveAttributesGetter().getDestination(request)).isNull();
    assertThat(spanAttributes.build()).isEqualTo(Attributes.empty());
    assertThat(links.attributes).containsExactly(linkAttributes("topic-a", "0", 5, "key"));
    assertThat(links.linksWithAttributes).isEqualTo(1);
  }

  @Test
  void keepsLegacyLinksUnchanged() {
    assumeFalse(emitStableMessagingSemconv());
    KafkaReceiveRequest request =
        request(record("topic-a", 1, 10, "key-a"), record("topic-b", 2, 20, "key-b"));

    RecordingSpanLinksBuilder links = extractLinks(request);

    assertThat(links.attributes).containsExactly(Attributes.empty(), Attributes.empty());
    assertThat(links.linksWithoutAttributes).isEqualTo(2);
  }

  @Test
  void doesNotSerializeByteBufferKey() {
    assertThat(KafkaUtil.serializeKey(ByteBuffer.wrap(new byte[] {1}))).isNull();
  }

  private static RecordingSpanLinksBuilder extractLinks(KafkaReceiveRequest request) {
    RecordingSpanLinksBuilder links = new RecordingSpanLinksBuilder();
    new KafkaBatchProcessSpanLinksExtractor(new TestPropagator())
        .extract(links, Context.root(), request);
    return links;
  }

  @SafeVarargs
  private static KafkaReceiveRequest request(ConsumerRecord<String, String>... records) {
    Map<TopicPartition, List<ConsumerRecord<String, String>>> recordsByPartition =
        new LinkedHashMap<>();
    for (ConsumerRecord<String, String> record : records) {
      recordsByPartition
          .computeIfAbsent(
              new TopicPartition(record.topic(), record.partition()), unused -> new ArrayList<>())
          .add(record);
    }
    return KafkaReceiveRequest.create(new ConsumerRecords<>(recordsByPartition), null, null);
  }

  private static ConsumerRecord<String, String> record(
      String topic, int partition, long offset, String key) {
    return new ConsumerRecord<>(topic, partition, offset, key, "value");
  }

  private static Attributes linkAttributes(
      @Nullable String destination, @Nullable String partition, long offset, String key) {
    AttributesBuilder attributes = Attributes.builder();
    attributes.put(MESSAGING_DESTINATION_NAME, destination);
    attributes.put(MESSAGING_DESTINATION_PARTITION_ID, partition);
    return attributes
        .put(MESSAGING_KAFKA_OFFSET, offset)
        .put(MESSAGING_KAFKA_MESSAGE_KEY, key)
        .build();
  }

  private static final class RecordingSpanLinksBuilder implements SpanLinksBuilder {
    private final List<Attributes> attributes = new ArrayList<>();
    private int linksWithoutAttributes;
    private int linksWithAttributes;

    @Override
    public SpanLinksBuilder addLink(SpanContext spanContext) {
      attributes.add(Attributes.empty());
      linksWithoutAttributes++;
      return this;
    }

    @Override
    public SpanLinksBuilder addLink(SpanContext spanContext, Attributes attributes) {
      this.attributes.add(attributes);
      linksWithAttributes++;
      return this;
    }
  }

  private static final class TestPropagator implements TextMapPropagator {
    @Override
    public List<String> fields() {
      return emptyList();
    }

    @Override
    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {}

    @Override
    public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
      return Context.root().with(Span.wrap(LINK_CONTEXT));
    }
  }
}
