/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.springframework.util.ErrorHandler;

public final class ErrorHandlerWrapper implements ErrorHandler {
  private final ErrorHandler errorHandler;

  public ErrorHandlerWrapper(ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }

  @Override
  public void handleError(Throwable throwable) {
    Context taskContext = TaskContextHolder.getTaskContext(Context.current());
    // run the error handler with the same context as task execution
    try (Scope ignore = taskContext != null ? taskContext.makeCurrent() : null) {
      errorHandler.handleError(throwable);
    }
  }
}
