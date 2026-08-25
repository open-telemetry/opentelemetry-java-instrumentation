/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertConsumedMessagesMetrics;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetrics;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricsWithConsumedMessages;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertTotalConsumedMessages;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.util.Collection;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that the Connect process operation counts {@code messaging.client.consumed.messages}
 * once per delivery attempt, including failed and redelivered attempts, while not double-counting
 * deliveries that a kafka-clients receive operation already owns.
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

  @Test
  @SuppressWarnings("unchecked")
  void receiveOwnedRecordIsNotCountedAgainByProcessOperation() {
    String receiveInstrumentationName = "test-kafka-connect-receive-owned";
    Instrumenter<KafkaReceiveRequest, Void> receiveInstrumenter =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), receiveInstrumentationName)
            .setMessagingReceiveTelemetryEnabled(true)
            .createConsumerReceiveInstrumenter();
    Consumer<String, String> consumer = mock(Consumer.class);
    receive(receiveInstrumenter, consumer, "receive-owned-topic", 0, 10);
    SinkRecord target = sinkRecord("receive-owned-topic", 0, 10);
    markReceiveOwned(target);

    new RetryingSinkTask(0).put(singletonList(target));

    // the receive operation already reported this delivery, so the process operation records its
    // own duration but must not add a second consumed-message count
    assertProcessMetrics(testing, INSTRUMENTATION_NAME, "receive-owned-topic", null, "0", 1, null);
    assertConsumedMessagesMetrics(
        testing, receiveInstrumentationName, "receive-owned-topic", null, "0", 1, null);
  }

  // Test classes are not rewritten to use the javaagent's field-backed VirtualField, so a
  // SinkRecord created directly in a test cannot be marked receive-owned through
  // KafkaConnectTask.copyReceiveOperation the way the real convertAndTransformRecord advice does:
  // the advice class is injected as an agent helper and reads the ConsumerRecord's context through
  // a shaded copy of the propagation classes, which a plain test class does not share. Instead,
  // reach the generated accessor that the agent weaves directly onto SinkRecord for the
  // VirtualField<SinkRecord, Boolean> declared in KafkaConnectTask, and set it the same way the
  // real accessor would.
  private static void markReceiveOwned(SinkRecord sinkRecord) {
    String fieldSuffix =
        SinkRecord.class.getName().replace('.', '$')
            + "$"
            + Boolean.class.getName().replace('.', '$');
    try {
      Class<?> accessor =
          Class.forName(
              "io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessor$" + fieldSuffix,
              false,
              SinkRecord.class.getClassLoader());
      accessor
          .getMethod("__set__opentelemetryVirtualField$" + fieldSuffix, Object.class)
          .invoke(sinkRecord, true);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("Could not mark the record as receive-owned", e);
    }
  }

  private static SinkRecord sinkRecord(String topic, int partition, long offset) {
    return new SinkRecord(topic, partition, null, null, null, null, offset);
  }

  private static void receive(
      Instrumenter<KafkaReceiveRequest, Void> receiveInstrumenter,
      Consumer<String, String> consumer,
      String topic,
      int partition,
      long offset) {
    ConsumerRecord<String, String> source =
        new ConsumerRecord<>(topic, partition, offset, "key", "value");
    ConsumerRecords<String, String> records =
        new ConsumerRecords<>(
            singletonMap(new TopicPartition(topic, partition), singletonList(source)));
    KafkaReceiveRequest request = KafkaReceiveRequest.create(records, consumer);
    Context receiveContext = receiveInstrumenter.start(Context.root(), request);
    receiveInstrumenter.end(receiveContext, request, null, null);
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
