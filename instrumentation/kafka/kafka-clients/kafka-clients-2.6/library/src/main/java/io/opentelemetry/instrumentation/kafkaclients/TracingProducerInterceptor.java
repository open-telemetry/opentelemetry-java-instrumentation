/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import io.opentelemetry.api.GlobalOpenTelemetry;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * A ProducerInterceptor that adds tracing capability. Add this interceptor's class name or class
 * via ProducerConfig.INTERCEPTOR_CLASSES_CONFIG property to your Producer's properties to get it
 * instantiated and used. See more details on ProducerInterceptor usage in its Javadoc.
 */
public class TracingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

  private static final KafkaTelemetry telemetry = KafkaTelemetry.create(GlobalOpenTelemetry.get());

  @Override
  public ProducerRecord<K, V> onSend(ProducerRecord<K, V> producerRecord) {
    telemetry.buildAndInjectSpan(producerRecord);
    return producerRecord;
  }

  @Override
  public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {}

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> map) {}
}
