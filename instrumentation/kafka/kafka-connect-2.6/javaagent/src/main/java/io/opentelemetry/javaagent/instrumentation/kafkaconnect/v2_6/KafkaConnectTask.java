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

  private Set<String> getTopics() {
    return records.stream()
        .map(SinkRecord::topic)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

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
