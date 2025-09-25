/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.connect.sink.SinkRecord;

public final class KafkaConnectTask {

  private final Collection<SinkRecord> records;

  public KafkaConnectTask(Collection<SinkRecord> records) {
    this.records = records;
  }

  public Collection<SinkRecord> getRecords() {
    return records;
  }

  /**
   * Returns the first record in the batch, used for extracting destination information.
   * Note: Records in a batch can come from multiple topics, so this should only be used
   * when you need a representative record, not for definitive topic information.
   */
  public SinkRecord getFirstRecord() {
    return records.isEmpty() ? null : records.iterator().next();
  }

  /**
   * Returns all unique topic names present in this batch of records.
   * This provides accurate information about all topics being processed.
   */
  public Set<String> getTopics() {
    return records.stream()
        .map(SinkRecord::topic)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Returns a single topic name if all records are from the same topic,
   * or a formatted string representing multiple topics if they differ.
   * This is useful for span naming that needs to be concise but informative.
   */
  public String getDestinationName() {
    Set<String> topics = getTopics();
    if (topics.isEmpty()) {
      return null;
    }
    if (topics.size() == 1) {
      return topics.iterator().next();
    }
    // For multiple topics, create a descriptive name
    return topics.stream().collect(Collectors.joining(",", "[", "]"));
  }
}
