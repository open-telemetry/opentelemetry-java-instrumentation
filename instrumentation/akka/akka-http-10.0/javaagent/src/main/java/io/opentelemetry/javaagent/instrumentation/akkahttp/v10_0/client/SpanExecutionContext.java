/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import static java.util.logging.Level.FINE;

import java.util.logging.Logger;
import scala.concurrent.ExecutionContext;

final class SpanExecutionContext implements ExecutionContext {
  private static final Logger logger = Logger.getLogger(SpanExecutionContext.class.getName());
  static final ExecutionContext INSTANCE = new SpanExecutionContext();

  private SpanExecutionContext() {}

  @Override
  public void execute(Runnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      reportFailure(t);
    }
  }

  @Override
  public void reportFailure(Throwable t) {
    logger.log(FINE, "Unable to end Akka HTTP client span", t);
  }

  @Override
  public ExecutionContext prepare() {
    return this;
  }
}
