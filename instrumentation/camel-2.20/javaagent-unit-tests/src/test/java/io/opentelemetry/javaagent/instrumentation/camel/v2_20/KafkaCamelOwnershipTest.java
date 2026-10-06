/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaProcessingOwnershipUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KafkaCamelOwnershipTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void marksOnlyCamelConsumerProcessing(boolean camelConsumer) {
    KafkaConsumer<?, ?> consumer = mock(KafkaConsumer.class);
    ConsumerRecord<String, String> record = new ConsumerRecord<>("test", 0, 0, "key", "value");
    ConsumerRecords<?, ?> records =
        new ConsumerRecords<>(singletonMap(new TopicPartition("test", 0), singletonList(record)));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    if (camelConsumer) {
      KafkaFetchRecordsInstrumentation.MarkConsumerAdvice.onEnter(consumer);
    }

    KafkaConsumerInstrumentation.PollAdvice.onExit(consumer, records);

    assertThat(
            KafkaProcessingOwnershipUtil.rawProcessingEligibility(records, () -> true)
                .getAsBoolean())
        .isEqualTo(!camelConsumer || !emitStableMessagingSemconv());
    assertThat(KafkaConsumerContextUtil.getRawProcessingEligibility(record).getAsBoolean())
        .isEqualTo(!camelConsumer || !emitStableMessagingSemconv());
  }
}
