/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import java.util.Collection;
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
   * Returns the first record in the batch, used for extracting destination information. Kafka
   * Connect processes records in batches, but all records in a batch typically come from the same
   * topic, so we use the first record for span naming.
   */
  public SinkRecord getFirstRecord() {
    return records.isEmpty() ? null : records.iterator().next();
  }
}
