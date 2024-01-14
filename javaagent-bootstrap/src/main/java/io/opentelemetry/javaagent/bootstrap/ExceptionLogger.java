/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static java.util.logging.Level.FINE;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Class used for exception handler logging.
 *
 * <p>See io.opentelemetry.javaagent.tooling.ExceptionHandlers
 */
public final class ExceptionLogger {

  private static final Logger logger = Logger.getLogger(ExceptionLogger.class.getName());
  private static final AtomicInteger counter = new AtomicInteger();

  /** See {@code io.opentelemetry.javaagent.tooling.ExceptionHandlers} for usages. */
  @SuppressWarnings("unused")
  public static void logSuppressedError(String message, Throwable error) {
    logger.log(FINE, message, error);
    counter.incrementAndGet();
  }

  // only used by tests
  public static int getAndReset() {
    return counter.getAndSet(0);
  }

  private ExceptionLogger() {}
}
