/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.tooling.internal.AgentCircularityLock;
import javax.annotation.Nullable;

/**
 * Keeps application logger creation and calls outside Byte Buddy's circularity lock.
 *
 * <p>Application logging can load classes that would miss instrumentation while the lock is held.
 * Records raised in that window are dropped.
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
      if (AgentCircularityLock.INSTANCE.isHeldByCurrentThread()) {
        return false;
      }
      return actual().isLoggable(level);
    }

    @Override
    public void log(InternalLogger.Level level, String message, @Nullable Throwable error) {
      if (AgentCircularityLock.INSTANCE.isHeldByCurrentThread()) {
        return;
      }
      actual().log(level, message, error);
    }

    @Override
    public String name() {
      return name;
    }

    // The factory's create() may run while the circularity lock is held.
    // Defer application logger creation until log() or isLoggable() passes the lock
    // check, since creation itself can load classes that would miss instrumentation.
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
