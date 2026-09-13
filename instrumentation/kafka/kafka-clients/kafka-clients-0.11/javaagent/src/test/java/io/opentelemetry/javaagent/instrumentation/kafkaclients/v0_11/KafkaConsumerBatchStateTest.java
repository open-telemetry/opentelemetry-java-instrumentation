/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaConsumerBatchStateTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void shouldHonorClaimBeforeIteratorCreation() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchStateUtil.recordPoll(records, true);
    KafkaConsumerBatchStateUtil.claimProcessSpan(records);

    Iterator<ConsumerRecord<String, String>> iterator = records.iterator();
    iterator.next();
    assertThat(iterator.hasNext()).isFalse();

    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void shouldHonorClaimAfterIteratorCreation() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchStateUtil.recordPoll(records, true);

    Iterator<ConsumerRecord<String, String>> iterator = records.iterator();
    KafkaConsumerBatchStateUtil.claimProcessSpan(records);
    iterator.next();
    assertThat(iterator.hasNext()).isFalse();

    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void shouldNotTraceFrameworkPoll() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchStateUtil.recordPoll(records, false);

    Iterator<ConsumerRecord<String, String>> iterator = records.iterator();
    iterator.next();
    assertThat(iterator.hasNext()).isFalse();

    assertThat(testing.spans()).isEmpty();
  }

  private static ConsumerRecords<String, String> records() {
    TopicPartition partition = new TopicPartition("test", 0);
    return new ConsumerRecords<>(singletonMap(partition, singletonList(record())));
  }

  private static ConsumerRecord<String, String> record() {
    return new ConsumerRecord<>("test", 0, 0, "key", "value");
  }
}
