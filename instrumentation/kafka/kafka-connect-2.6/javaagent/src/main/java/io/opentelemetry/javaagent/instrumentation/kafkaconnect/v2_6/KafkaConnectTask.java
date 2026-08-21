/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static java.util.stream.Collectors.toCollection;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.connect.sink.SinkRecord;

public class KafkaConnectTask {

  // Sink tasks run in a Kafka Connect plugin classloader, which has its own copy of the
  // instrumentation helper classes. A JDK type is used as the field type so that this
  // classloader and the worker classloader generate the same VirtualField accessor.
  private static final VirtualField<SinkRecord, Boolean> RECEIVE_OWNED_FIELD =
      VirtualField.find(SinkRecord.class, Boolean.class);

  private final Collection<SinkRecord> records;
  @Nullable private KafkaConnectBatchRecordAttributes batchRecordAttributes;

  public KafkaConnectTask(Collection<SinkRecord> records) {
    this.records = records;
  }

  public Collection<SinkRecord> getRecords() {
    return records;
  }

  // both the attributes extractor and the span links extractor need this, and they are always
  // called on the same thread while the span is being started
  KafkaConnectBatchRecordAttributes getBatchRecordAttributes() {
    if (batchRecordAttributes == null) {
      batchRecordAttributes = KafkaConnectBatchRecordAttributes.create(records);
    }
    return batchRecordAttributes;
  }

  // marks the transformed SinkRecord as receive-owned when the source ConsumerRecord was already
  // counted by a kafka-clients receive operation, so the Connect process operation does not count
  // the same delivery a second time. target is null when the transform filtered the record.
  public static void copyReceiveOperation(
      ConsumerRecord<?, ?> source, @Nullable SinkRecord target) {
    if (target == null) {
      return;
    }
    KafkaConsumerContext consumerContext = KafkaConsumerContextUtil.get(source);
    Context context = consumerContext.getContext();
    if (context != null && KafkaConsumerContextUtil.hasReceiveOperation(context)) {
      RECEIVE_OWNED_FIELD.set(target, true);
    }
  }

  // counts the records of this put() invocation that were not already counted by a receive
  // operation, so that a batch partially owned by receive telemetry counts only the remainder,
  // and every put() invocation, including failed and redelivered ones, counts its own attempt.
  // The marker is cleared after suppressing a record so that a retry of the same put() — which
  // Kafka Connect performs when the task throws a RetriableException — is counted as a new attempt.
  long countUnmarkedRecords() {
    long count = 0;
    for (SinkRecord record : records) {
      if (Boolean.TRUE.equals(RECEIVE_OWNED_FIELD.get(record))) {
        RECEIVE_OWNED_FIELD.set(record, null);
      } else {
        count++;
      }
    }
    return count;
  }

  private Set<String> getTopics() {
    return records.stream().map(SinkRecord::topic).collect(toCollection(LinkedHashSet::new));
  }

  @Nullable
  public String getDestinationName() {
    Set<String> topics = getTopics();
    if (topics.isEmpty()) {
      return null;
    }
    // Return the topic name only if all records are from the same topic.
    // When records are from multiple topics, return null as there is no standard way
    // to represent multiple destination names in messaging.destination.name attribute.
    if (topics.size() == 1) {
      return topics.iterator().next();
    }
    return null;
  }
}
