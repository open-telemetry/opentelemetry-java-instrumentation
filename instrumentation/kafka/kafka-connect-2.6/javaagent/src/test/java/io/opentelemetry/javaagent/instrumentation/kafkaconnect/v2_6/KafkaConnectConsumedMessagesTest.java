/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricsWithConsumedMessages;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertTotalConsumedMessages;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.util.Collection;
import java.util.Map;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that the Connect process operation counts {@code messaging.client.consumed.messages}
 * once per delivery attempt, including failed and redelivered attempts.
 */
class KafkaConnectConsumedMessagesTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-connect-2.6";

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void failedAttemptAndSuccessfulRetryAreBothCounted() {
    RetryingSinkTask task = new RetryingSinkTask(1);
    SinkRecord record = sinkRecord("failed-retry-topic", 0, 10);

    assertThatThrownBy(() -> task.put(singletonList(record)))
        .isInstanceOf(RetriableException.class);
    task.put(singletonList(record));

    assertProcessMetricsWithConsumedMessages(
        testing,
        INSTRUMENTATION_NAME,
        "failed-retry-topic",
        null,
        "0",
        1,
        1,
        RetriableException.class.getName());
    assertProcessMetricsWithConsumedMessages(
        testing, INSTRUMENTATION_NAME, "failed-retry-topic", null, "0", 1, 1, null);
    // both the failed attempt and its retry deliver the record, so both must be counted
    assertTotalConsumedMessages(testing, INSTRUMENTATION_NAME, 2);
  }

  private static SinkRecord sinkRecord(String topic, int partition, long offset) {
    return new SinkRecord(topic, partition, null, null, null, null, offset);
  }

  private static class RetryingSinkTask extends SinkTask {
    private final int failures;
    private int attempts;

    RetryingSinkTask(int failures) {
      this.failures = failures;
    }

    @Override
    public String version() {
      return "test";
    }

    @Override
    public void start(Map<String, String> properties) {}

    @Override
    public void put(Collection<SinkRecord> records) {
      if (attempts++ < failures) {
        throw new RetriableException("retry");
      }
    }

    @Override
    public void stop() {}
  }
}
