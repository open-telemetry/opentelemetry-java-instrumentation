/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

final class JdkErrorCauseExtractor implements ErrorCauseExtractor {
  static final ErrorCauseExtractor INSTANCE = new JdkErrorCauseExtractor();

  @Nullable
  private static final Class<?> COMPLETION_EXCEPTION_CLASS = getCompletionExceptionClass();

  @Override
  public Throwable extract(Throwable error) {
    if (error.getCause() != null
        && (error instanceof ExecutionException
            || isInstanceOfCompletionException(error)
            || error instanceof InvocationTargetException
            || error instanceof UndeclaredThrowableException)) {
      return extract(error.getCause());
    }
    return error;
  }

  private static boolean isInstanceOfCompletionException(Throwable error) {
    return COMPLETION_EXCEPTION_CLASS != null && COMPLETION_EXCEPTION_CLASS.isInstance(error);
  }

  @Nullable
  private static Class<?> getCompletionExceptionClass() {
    try {
      return Class.forName("java.util.concurrent.CompletionException");
    } catch (ClassNotFoundException e) {
      // Android level 21 does not support java.util.concurrent.CompletionException
      return null;
    }
  }

  private JdkErrorCauseExtractor() {}
}
