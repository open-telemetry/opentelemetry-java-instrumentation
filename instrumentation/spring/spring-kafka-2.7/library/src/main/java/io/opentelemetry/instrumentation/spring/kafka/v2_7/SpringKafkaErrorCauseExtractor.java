/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

enum SpringKafkaErrorCauseExtractor implements ErrorCauseExtractor {
  INSTANCE;

  @Override
  public Throwable extract(Throwable error) {
    if (error instanceof ListenerExecutionFailedException && error.getCause() != null) {
      error = error.getCause();
    }
    return ErrorCauseExtractor.jdk().extract(error);
  }
}
