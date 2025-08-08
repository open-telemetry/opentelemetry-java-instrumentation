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

  public static String getSpanName(KafkaConnectTask task) {
    return "KafkaConnect.put";
  }
}
