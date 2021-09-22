/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.KafkaUtils;
import io.opentelemetry.instrumentation.kafka.ReceivedRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class KafkaSingletons {
  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11.javaagent";

  private static final Instrumenter<ProducerRecord<?, ?>, Void> PRODUCER_INSTRUMENTER =
      KafkaUtils.buildProducerInstrumenter(INSTRUMENTATION_NAME);
  private static final Instrumenter<ReceivedRecords, Void> CONSUMER_RECEIVE_INSTRUMENTER =
      KafkaUtils.buildConsumerReceiveInstrumenter(INSTRUMENTATION_NAME);
  private static final Instrumenter<ConsumerRecord<?, ?>, Void> CONSUMER_PROCESS_INSTRUMENTER =
      KafkaUtils.buildConsumerProcessInstrumenter(INSTRUMENTATION_NAME);

  public static Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<ReceivedRecords, Void> consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> consumerProcessInstrumenter() {
    return CONSUMER_PROCESS_INSTRUMENTER;
  }

  private KafkaSingletons() {}
}
