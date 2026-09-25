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
    KafkaProcessingOwnershipUtil.recordPoll(records, true);

    assertThat(
            KafkaProcessingOwnershipUtil.rawProcessingEligibility(records, () -> true)
                .getAsBoolean())
        .isTrue();
  }

  @Test
  void shouldHonorClaimBeforeIteratorCreation() {
    ConsumerRecords<String, String> records = records();
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    KafkaProcessingOwnershipUtil.markProcessingOwnedOutsideKafkaClient(records);

    BooleanSupplier rawProcessingEligibility =
        KafkaProcessingOwnershipUtil.rawProcessingEligibility(records, () -> true);
    assertThat(rawProcessingEligibility.getAsBoolean()).isFalse();
  }

  @Test
  void shouldHonorClaimAfterIteratorCreation() {
    ConsumerRecords<String, String> records = records();
    KafkaProcessingOwnershipUtil.recordPoll(records, true);

    BooleanSupplier rawProcessingEligibility =
        KafkaProcessingOwnershipUtil.rawProcessingEligibility(records, () -> true);
    KafkaProcessingOwnershipUtil.markProcessingOwnedOutsideKafkaClient(records);

    assertThat(rawProcessingEligibility.getAsBoolean()).isFalse();
  }

  @Test
  void shouldNotTraceFrameworkPoll() {
    ConsumerRecords<String, String> records = records();
    KafkaProcessingOwnershipUtil.recordPoll(records, false);

    assertThat(
            KafkaProcessingOwnershipUtil.rawProcessingEligibility(records, () -> true)
                .getAsBoolean())
        .isFalse();
  }

  @Test
  void shouldHonorFrameworkSuppressionAfterApplicationPoll() {
    ConsumerRecords<String, String> records = records();
    KafkaProcessingOwnershipUtil.recordPoll(records, true);

    assertThat(
            KafkaProcessingOwnershipUtil.rawProcessingEligibility(records, () -> false)
                .getAsBoolean())
        .isFalse();
  }

  @Test
  void newPollRestoresFirstTraversalForReusedBatch() {
    ConsumerRecords<String, String> records = records();
    KafkaProcessingOwnershipUtil.recordPoll(records, true);

    assertThat(KafkaProcessingOwnershipUtil.claimFirstTraversal(records)).isTrue();
    assertThat(KafkaProcessingOwnershipUtil.claimFirstTraversal(records)).isFalse();

    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    assertThat(KafkaProcessingOwnershipUtil.claimFirstTraversal(records)).isTrue();
  }

  private static ConsumerRecords<String, String> records() {
    TopicPartition partition = new TopicPartition("test", 0);
    return new ConsumerRecords<>(singletonMap(partition, singletonList(record())));
  }

  private static ConsumerRecord<String, String> record() {
    return new ConsumerRecord<>("test", 0, 0, "key", "value");
  }
}
