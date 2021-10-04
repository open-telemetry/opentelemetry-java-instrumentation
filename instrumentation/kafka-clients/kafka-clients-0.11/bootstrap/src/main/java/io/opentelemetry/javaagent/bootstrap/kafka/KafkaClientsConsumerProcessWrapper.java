/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

public interface KafkaClientsConsumerProcessWrapper<T> {

  /**
   * Returns the actual, non-tracing object wrapped by this wrapper. This method is only supposed to
   * be used by other Kafka consumer instrumentations that want to suppress the kafka-clients one.
   */
  T unwrap();
}
