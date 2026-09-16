/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaConsumerBatchStateTest {

  @Test
  void shouldTraceUnclaimedApplicationPoll() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchStateUtil.recordPoll(records, null);

    assertThat(KafkaConsumerBatchStateUtil.processSpanEnabled(records, () -> true).getAsBoolean())
        .isTrue();
  }

  @Test
  void shouldHonorClaimBeforeIteratorCreation() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchStateUtil.recordPoll(records, null);
    KafkaConsumerBatchStateUtil.claimProcessSpan(records);

    BooleanSupplier processSpanEnabled =
        KafkaConsumerBatchStateUtil.processSpanEnabled(records, () -> true);
    assertThat(processSpanEnabled.getAsBoolean()).isFalse();
  }

  @Test
  void shouldHonorClaimAfterIteratorCreation() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchStateUtil.recordPoll(records, null);

    BooleanSupplier processSpanEnabled =
        KafkaConsumerBatchStateUtil.processSpanEnabled(records, () -> true);
    KafkaConsumerBatchStateUtil.claimProcessSpan(records);

    assertThat(processSpanEnabled.getAsBoolean()).isFalse();
  }

  @Test
  void shouldNotTraceFrameworkPoll() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchStateUtil.recordPoll(records, Boolean.TRUE);

    assertThat(KafkaConsumerBatchStateUtil.processSpanEnabled(records, () -> true).getAsBoolean())
        .isFalse();
  }

  @Test
  void shouldHonorFrameworkSuppressionAfterApplicationPoll() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchStateUtil.recordPoll(records, null);

    assertThat(KafkaConsumerBatchStateUtil.processSpanEnabled(records, () -> false).getAsBoolean())
        .isFalse();
  }

  private static ConsumerRecords<String, String> records() {
    TopicPartition partition = new TopicPartition("test", 0);
    return new ConsumerRecords<>(singletonMap(partition, singletonList(record())));
  }

  private static ConsumerRecord<String, String> record() {
    return new ConsumerRecord<>("test", 0, 0, "key", "value");
  }
}
