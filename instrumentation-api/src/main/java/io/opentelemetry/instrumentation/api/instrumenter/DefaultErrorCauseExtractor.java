/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

final class DefaultErrorCauseExtractor implements ErrorCauseExtractor {
  static final ErrorCauseExtractor INSTANCE = new DefaultErrorCauseExtractor();

  @Nullable
  private static final Class<?> COMPLETION_EXCEPTION_CLASS = getCompletionExceptionClass();

  @Override
  public Throwable extract(Throwable error) {
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = error;
    while (current.getCause() != null && isUnwrappableWrapper(current)) {
      if (!visited.add(current)) {
        break;
      }
      current = current.getCause();
    }
    return current;
  }

  private static boolean isUnwrappableWrapper(Throwable error) {
    return error instanceof ExecutionException
        || isInstanceOfCompletionException(error)
        || error instanceof InvocationTargetException
        || error instanceof UndeclaredThrowableException;
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

  private DefaultErrorCauseExtractor() {}
}
