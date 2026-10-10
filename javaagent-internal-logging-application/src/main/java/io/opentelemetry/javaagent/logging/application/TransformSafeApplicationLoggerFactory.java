/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.internal.InTransformation;
import javax.annotation.Nullable;

/**
 * Wraps the application logger factory so that the agent never calls into the application logging
 * system while a class file transformation is in progress on the current thread.
 *
 * <p>Writing a log record from inside a transformation makes the logging framework load classes,
 * and classes loaded in that window are defined without instrumentation - which silently disables
 * the instrumentation of the very framework the agent is logging through. Records raised in that
 * window are dropped instead.
 */
final class TransformSafeApplicationLoggerFactory implements InternalLogger.Factory {

  private final InternalLogger.Factory delegate;

  TransformSafeApplicationLoggerFactory(InternalLogger.Factory delegate) {
    this.delegate = delegate;
  }

  @Override
  public InternalLogger create(String name) {
    return new TransformSafeLogger(name);
  }

  private final class TransformSafeLogger implements InternalLogger {

    private final String name;
    @Nullable private volatile InternalLogger actual;

    TransformSafeLogger(String name) {
      this.name = name;
    }

    @Override
    public boolean isLoggable(InternalLogger.Level level) {
      if (InTransformation.get()) {
        // the application logging system can't be consulted here, so let the caller skip
        // building the record entirely
        return false;
      }
      return actual().isLoggable(level);
    }

    @Override
    public void log(InternalLogger.Level level, String message, @Nullable Throwable error) {
      if (InTransformation.get()) {
        // callers that don't check isLoggable() first still have to be safe here - drop the
        // record rather than call into the application logging system
        return;
      }
      actual().log(level, message, error);
    }

    @Override
    public String name() {
      return name;
    }

    // created lazily - instantiating the bridged logger calls into the application logging system,
    // which must not happen while a transformation is in progress
    private InternalLogger actual() {
      InternalLogger logger = actual;
      if (logger == null) {
        logger = delegate.create(name);
        actual = logger;
      }
      return logger;
    }
  }
}
