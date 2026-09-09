/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;

class KafkaConnectBatchRecordAttributesTest {

  @Test
  void keepsCommonPartitionOnBatchSpan() {
    List<SinkRecord> records = asList(record("topic", 1, 10, "key"), record("topic", 1, 10, "key"));
    KafkaConnectBatchRecordAttributes attributes =
        KafkaConnectBatchRecordAttributes.create(records);

    // the destination is emitted by MessagingAttributesExtractor, not by the class under test
    assertThat(new KafkaConnectTask(records).getDestinationName()).isEqualTo("topic");
    assertThat(commonAttributes(attributes))
        .isEqualTo(Attributes.builder().put(MESSAGING_DESTINATION_PARTITION_ID, "1").build());
    // the offset and the message key stay on the links even though they are the same for every
    // record, because they are only recommended on spans that describe a single message operation
    assertThat(linkAttributes(attributes, records))
        .containsExactly(
            expectedLinkAttributes(null, null, 10L, "key"),
            expectedLinkAttributes(null, null, 10L, "key"));
  }

  @Test
  void keepsOffsetAndKeyOnLinkOfSingleRecordBatch() {
    List<SinkRecord> records = singletonList(record("topic", 1, 10, "key"));
    KafkaConnectBatchRecordAttributes attributes =
        KafkaConnectBatchRecordAttributes.create(records);

    assertThat(commonAttributes(attributes))
        .isEqualTo(Attributes.builder().put(MESSAGING_DESTINATION_PARTITION_ID, "1").build());
    assertThat(linkAttributes(attributes, records))
        .containsExactly(expectedLinkAttributes(null, null, 10L, "key"));
  }

  @Test
  void movesDifferingValuesToRecordLinks() {
    List<SinkRecord> records =
        asList(record("topic-a", 1, 10, "key-a"), record("topic-b", 2, 20, "key-b"));
    KafkaConnectBatchRecordAttributes attributes =
        KafkaConnectBatchRecordAttributes.create(records);

    assertThat(new KafkaConnectTask(records).getDestinationName()).isNull();
    assertThat(commonAttributes(attributes)).isEqualTo(Attributes.empty());
    assertThat(linkAttributes(attributes, records))
        .containsExactly(
            expectedLinkAttributes("topic-a", "1", 10L, "key-a"),
            expectedLinkAttributes("topic-b", "2", 20L, "key-b"));
  }

  @Test
  void movesPartitionToRecordLinksWhenDestinationVaries() {
    List<SinkRecord> records =
        asList(record("topic-a", 0, 5, "key"), record("topic-b", 0, 6, "key"));
    KafkaConnectBatchRecordAttributes attributes =
        KafkaConnectBatchRecordAttributes.create(records);

    // the batch spans two topics, so no destination name is emitted on the batch span, which would
    // leave the partition id orphaned there
    assertThat(new KafkaConnectTask(records).getDestinationName()).isNull();
    assertThat(commonAttributes(attributes)).isEqualTo(Attributes.empty());
    assertThat(linkAttributes(attributes, records))
        .containsExactly(
            expectedLinkAttributes("topic-a", "0", 5L, "key"),
            expectedLinkAttributes("topic-b", "0", 6L, "key"));
  }

  @Test
  void keepsDestinationOnBatchSpanWhenOnlyPartitionVaries() {
    List<SinkRecord> records = asList(record("topic", 0, 5, "key"), record("topic", 1, 5, "key"));
    KafkaConnectBatchRecordAttributes attributes =
        KafkaConnectBatchRecordAttributes.create(records);

    assertThat(new KafkaConnectTask(records).getDestinationName()).isEqualTo("topic");
    assertThat(commonAttributes(attributes)).isEqualTo(Attributes.empty());
    assertThat(linkAttributes(attributes, records))
        .containsExactly(
            expectedLinkAttributes(null, "0", 5L, "key"),
            expectedLinkAttributes(null, "1", 5L, "key"));
  }

  @Test
  void omitsLinkOffsetWhenNoPartitionIsKnown() {
    List<SinkRecord> records =
        asList(
            recordWithoutPartition("topic", 5, "key"), recordWithoutPartition("topic", 5, "key"));
    KafkaConnectBatchRecordAttributes attributes =
        KafkaConnectBatchRecordAttributes.create(records);

    assertThat(commonAttributes(attributes)).isEqualTo(Attributes.empty());
    assertThat(linkAttributes(attributes, records))
        .containsExactly(
            expectedLinkAttributes(null, null, null, "key"),
            expectedLinkAttributes(null, null, null, "key"));
  }

  @Test
  void omitsLinkOffsetWhenPartitionIsMissing() {
    List<SinkRecord> records =
        asList(recordWithoutPartition("topic", 5, "key"), record("topic", 1, 6, "key"));
    KafkaConnectBatchRecordAttributes attributes =
        KafkaConnectBatchRecordAttributes.create(records);

    assertThat(commonAttributes(attributes)).isEqualTo(Attributes.empty());
    assertThat(linkAttributes(attributes, records))
        .containsExactly(
            expectedLinkAttributes(null, null, null, "key"),
            expectedLinkAttributes(null, "1", 6L, "key"));
  }

  private static Attributes commonAttributes(KafkaConnectBatchRecordAttributes attributes) {
    AttributesBuilder builder = Attributes.builder();
    attributes.putCommonAttributes(builder);
    return builder.build();
  }

  private static List<Attributes> linkAttributes(
      KafkaConnectBatchRecordAttributes attributes, List<SinkRecord> records) {
    return records.stream().map(attributes::getLinkAttributes).collect(toList());
  }

  private static Attributes expectedLinkAttributes(
      String destination, String partition, Long offset, String key) {
    return Attributes.builder()
        .put(MESSAGING_DESTINATION_NAME, destination)
        .put(MESSAGING_DESTINATION_PARTITION_ID, partition)
        .put(MESSAGING_KAFKA_OFFSET, offset)
        .put(MESSAGING_KAFKA_MESSAGE_KEY, key)
        .build();
  }

  @Test
  void countsReceiveOwnedRecordsOnRetry() {
    VirtualField<SinkRecord, Boolean> receiveOwnedField =
        VirtualField.find(SinkRecord.class, Boolean.class);
    SinkRecord owned = record("topic", 0, 1, "key");
    SinkRecord notOwned = record("topic", 0, 2, "key");
    receiveOwnedField.set(owned, true);

    KafkaConnectTask task = new KafkaConnectTask(asList(owned, notOwned));

    // First put(): the receive-owned record is not counted; the marker is cleared for the retry.
    assertThat(task.countUnmarkedRecords()).isEqualTo(1);
    // Retry put(): the marker was cleared, so both records are counted.
    assertThat(task.countUnmarkedRecords()).isEqualTo(2);
  }

  private static SinkRecord record(String topic, int partition, long offset, String key) {
    return new SinkRecord(
        topic, partition, Schema.STRING_SCHEMA, key, Schema.STRING_SCHEMA, "value", offset);
  }

  private static SinkRecord recordWithoutPartition(String topic, long offset, String key) {
    return new SinkRecord(
        topic, 0, Schema.STRING_SCHEMA, key, Schema.STRING_SCHEMA, "value", offset) {
      @Override
      public Integer kafkaPartition() {
        return null;
      }
    };
  }
}
