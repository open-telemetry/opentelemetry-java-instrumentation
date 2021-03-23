/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

final class JdkErrorCauseExtractor implements ErrorCauseExtractor {
  static final ErrorCauseExtractor INSTANCE = new JdkErrorCauseExtractor();

  @Override
  public Throwable extractCause(Throwable error) {
    if (error.getCause() != null
        && (error instanceof ExecutionException
            || error instanceof CompletionException
            || error instanceof InvocationTargetException
            || error instanceof UndeclaredThrowableException)) {
      return extractCause(error.getCause());
    }
    return error;
  }

  private JdkErrorCauseExtractor() {}
}
