/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import static java.util.logging.Level.WARNING;

import java.util.logging.Logger;
import scala.concurrent.ExecutionContext;

final class DirectExecutionContext implements ExecutionContext {

  private static final Logger logger = Logger.getLogger(DirectExecutionContext.class.getName());

  static final DirectExecutionContext INSTANCE = new DirectExecutionContext();

  private DirectExecutionContext() {}

  @Override
  public void execute(Runnable runnable) {
    runnable.run();
  }

  @Override
  public void reportFailure(Throwable cause) {
    logger.log(WARNING, "Error in Akka HTTP client callback", cause);
  }

  @Override
  public ExecutionContext prepare() {
    return this;
  }
}
