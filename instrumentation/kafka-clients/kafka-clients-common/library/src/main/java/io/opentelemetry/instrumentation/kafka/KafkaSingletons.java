/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka;

import static io.opentelemetry.instrumentation.kafka.KafkaUtils.buildConsumerProcessInstrumenter;
import static io.opentelemetry.instrumentation.kafka.KafkaUtils.buildConsumerReceiveInstrumenter;
import static io.opentelemetry.instrumentation.kafka.KafkaUtils.buildProducerInstrumenter;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class KafkaSingletons {
  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  private static final Instrumenter<ProducerRecord<?, ?>, Void> PRODUCER_INSTRUMENTER =
      buildProducerInstrumenter(INSTRUMENTATION_NAME);
  private static final Instrumenter<ReceivedRecords, Void> CONSUMER_RECEIVE_INSTRUMENTER =
      buildConsumerReceiveInstrumenter(INSTRUMENTATION_NAME);
  private static final Instrumenter<ConsumerRecord<?, ?>, Void> CONSUMER_PROCESS_INSTRUMENTER =
      buildConsumerProcessInstrumenter(INSTRUMENTATION_NAME);

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
