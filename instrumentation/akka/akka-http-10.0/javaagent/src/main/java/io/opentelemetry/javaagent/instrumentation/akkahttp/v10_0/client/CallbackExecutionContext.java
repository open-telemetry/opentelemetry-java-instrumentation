/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import java.util.concurrent.RejectedExecutionException;
import scala.concurrent.ExecutionContext;

final class CallbackExecutionContext implements ExecutionContext {
  private final ExecutionContext delegate;

  CallbackExecutionContext(ExecutionContext delegate) {
    this.delegate = delegate;
  }

  @Override
  public void execute(Runnable runnable) {
    try {
      delegate.execute(runnable);
    } catch (RejectedExecutionException e) {
      // Scala can swallow scheduling failures, leaving the replacement promise and span unfinished.
      try {
        runnable.run();
      } finally {
        reportFailure(e);
      }
    }
  }

  @Override
  public void reportFailure(Throwable cause) {
    delegate.reportFailure(cause);
  }

  @Override
  public ExecutionContext prepare() {
    return new CallbackExecutionContext(delegate.prepare());
  }
}
