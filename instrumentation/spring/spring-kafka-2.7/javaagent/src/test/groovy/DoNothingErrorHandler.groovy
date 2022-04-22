/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.ErrorHandler

class DoNothingErrorHandler implements ErrorHandler {
  @Override
  void handle(Exception thrownException, ConsumerRecord<?, ?> data) {
  }
}
