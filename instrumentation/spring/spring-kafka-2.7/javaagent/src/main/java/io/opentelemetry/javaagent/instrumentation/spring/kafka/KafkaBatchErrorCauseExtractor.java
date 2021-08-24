/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

public final class KafkaBatchErrorCauseExtractor implements ErrorCauseExtractor {
  private final ErrorCauseExtractor delegate = ErrorCauseExtractor.jdk();

  @Override
  public Throwable extractCause(Throwable error) {
    if (error instanceof ListenerExecutionFailedException && error.getCause() != null) {
      error = error.getCause();
    }
    return delegate.extractCause(error);
  }
}
