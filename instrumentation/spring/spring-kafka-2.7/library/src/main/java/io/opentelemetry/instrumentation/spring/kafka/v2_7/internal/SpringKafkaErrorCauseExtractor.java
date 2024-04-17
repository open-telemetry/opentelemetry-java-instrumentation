/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7.internal;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum SpringKafkaErrorCauseExtractor implements ErrorCauseExtractor {
  INSTANCE;

  @Override
  public Throwable extract(Throwable error) {
    if (error instanceof ListenerExecutionFailedException && error.getCause() != null) {
      error = error.getCause();
    }
    return ErrorCauseExtractor.getDefault().extract(error);
  }
}
