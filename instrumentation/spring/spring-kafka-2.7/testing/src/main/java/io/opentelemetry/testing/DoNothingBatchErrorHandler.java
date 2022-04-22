/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.BatchErrorHandler;

public class DoNothingBatchErrorHandler implements BatchErrorHandler {

  @Override
  public void handle(Exception thrownException, ConsumerRecords<?, ?> data) {}
}
