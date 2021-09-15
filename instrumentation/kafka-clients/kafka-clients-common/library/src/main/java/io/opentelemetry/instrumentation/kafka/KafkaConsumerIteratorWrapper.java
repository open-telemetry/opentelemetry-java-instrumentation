/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka;

import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface KafkaConsumerIteratorWrapper<K, V> {

  /**
   * Returns the actual, non-tracing iterator. This method is only supposed to be used by other
   * Kafka consumer instrumentations that want to suppress the kafka-clients one.
   */
  Iterator<ConsumerRecord<K, V>> unwrap();
}
