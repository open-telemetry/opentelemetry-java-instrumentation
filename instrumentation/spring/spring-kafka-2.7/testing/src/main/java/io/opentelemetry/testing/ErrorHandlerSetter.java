/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

// Base classes for error handlers are missing in the latest version. Setter methods were extracted
// in ConsumerConfig to avoid verifier attempting to load these classes.
class ErrorHandlerSetter {

  private ErrorHandlerSetter() {}

  static void setBatchErrorHandler(
      ConcurrentKafkaListenerContainerFactory<String, String> factory) {
    factory.setBatchErrorHandler(new DoNothingBatchErrorHandler());
  }

  static void setErrorHandler(ConcurrentKafkaListenerContainerFactory<String, String> factory) {
    factory.setErrorHandler(new DoNothingErrorHandler());
  }
}
