/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ErrorHandler;

public class DoNothingErrorHandler implements ErrorHandler {

  @Override
  public void handle(Exception thrownException, ConsumerRecord<?, ?> data) {}
}
