/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class KafkaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  private static final Instrumenter<ProducerRecord<?, ?>, Void> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<ConsumerRecords<?, ?>, Void> CONSUMER_RECEIVE_INSTRUMENTER;
  private static final Instrumenter<ConsumerRecord<?, ?>, Void> CONSUMER_PROCESS_INSTRUMENTER;

  static {
    KafkaInstrumenterFactory instrumenterFactory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME);
    PRODUCER_INSTRUMENTER = instrumenterFactory.createProducerInstrumenter();
    CONSUMER_RECEIVE_INSTRUMENTER = instrumenterFactory.createConsumerReceiveInstrumenter();
    CONSUMER_PROCESS_INSTRUMENTER = instrumenterFactory.createConsumerProcessInstrumenter();
  }

  public static Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<ConsumerRecords<?, ?>, Void> consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> consumerProcessInstrumenter() {
    return CONSUMER_PROCESS_INSTRUMENTER;
  }

  private KafkaSingletons() {}
}
