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
  void keepsSameValuesOnBatchSpan() {
    assumeTrue(emitStableMessagingSemconv());
    ConsumerRecord<String, String> first = record("topic", 1, 10, "key");
    ConsumerRecord<String, String> second = record("topic", 1, 10, "key");
    KafkaReceiveRequest request = request(first, second);

    AttributesBuilder spanAttributes = Attributes.builder();
    new KafkaReceiveAttributesExtractor().onStart(spanAttributes, Context.root(), request);
    RecordingSpanLinksBuilder links = extractLinks(request);

    assertThat(spanAttributes.build())
        .isEqualTo(
            Attributes.builder()
                .put(MESSAGING_DESTINATION_NAME, "topic")
                .put(MESSAGING_DESTINATION_PARTITION_ID, "1")
                .put(MESSAGING_KAFKA_OFFSET, 10)
                .put(MESSAGING_KAFKA_MESSAGE_KEY, "key")
                .build());
    assertThat(links.attributes).containsExactly(Attributes.empty(), Attributes.empty());
    assertThat(links.linksWithAttributes).isEqualTo(2);
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
            recordAttributes("topic-a", 1, 10, "key-a"),
            recordAttributes("topic-b", 2, 20, "key-b"));
    assertThat(links.linksWithAttributes).isEqualTo(2);
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

  private static Attributes recordAttributes(
      String destination, int partition, long offset, String key) {
    return Attributes.builder()
        .put(MESSAGING_DESTINATION_NAME, destination)
        .put(MESSAGING_DESTINATION_PARTITION_ID, Integer.toString(partition))
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
    public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {}

    @Override
    public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
      return Context.root().with(Span.wrap(LINK_CONTEXT));
    }
  }
}
