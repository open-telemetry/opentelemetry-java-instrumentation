/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface KafkaConsumerIterableWrapper<K, V> {

  /**
   * Returns the actual, non-tracing iterable. This method is only supposed to be used by other
   * Kafka consumer instrumentations that want to suppress the kafka-clients one.
   */
  Iterable<ConsumerRecord<K, V>> unwrap();
}
