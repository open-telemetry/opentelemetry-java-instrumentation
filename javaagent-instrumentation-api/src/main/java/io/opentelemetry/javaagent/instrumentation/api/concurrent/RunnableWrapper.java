/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is used to wrap lambda runnables since currently we cannot instrument them.
 *
 * <p>FIXME: We should remove this once https://github.com/raphw/byte-buddy/issues/558 is fixed
 */
public final class RunnableWrapper implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(RunnableWrapper.class);

  public static Runnable wrapIfNeeded(Runnable task) {
    // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
    // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
    if (task.getClass().getName().contains("/") && !(task instanceof RunnableWrapper)) {
      logger.debug("Wrapping runnable task {}", task);
      return new RunnableWrapper(task);
    }
    return task;
  }

  private final Runnable runnable;

  private RunnableWrapper(Runnable runnable) {
    this.runnable = runnable;
  }

  @Override
  public void run() {
    runnable.run();
  }
}
