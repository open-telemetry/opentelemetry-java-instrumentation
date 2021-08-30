/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.apache.kafka.clients.consumer.ConsumerRecords
import org.springframework.kafka.listener.BatchErrorHandler

class DoNothingBatchErrorHandler implements BatchErrorHandler {
  @Override
  void handle(Exception thrownException, ConsumerRecords<?, ?> data) {
  }
}
