/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

// Classes used by multiple instrumentations should be in a bootstrap module to ensure that all
// instrumentations see the same class. Helper classes are injected into each class loader that
// contains an instrumentation that uses them, so instrumentations in different class loaders will
// have separate copies of helper classes.
public interface KafkaClientsConsumerProcessWrapper<T> {

  /**
   * Returns the actual, non-tracing object wrapped by this wrapper. This method is only supposed to
   * be used by other Kafka consumer instrumentations that want to suppress the kafka-clients one.
   */
  T unwrap();
}
