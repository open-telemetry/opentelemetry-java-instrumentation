/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.logging;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;

/**
 * Keeps logger creation and calls outside Byte Buddy's circularity lock.
 *
 * <p>Logging can load classes that would miss instrumentation while the lock is held. Records
 * raised in that window are logged from a background thread.
 */
// visible for testing
public final class TransformSafeLoggerFactory implements InternalLogger.Factory {

  private final InternalLogger.Factory delegate;
  private final boolean enableMessageQueue;

  TransformSafeLoggerFactory(InternalLogger.Factory delegate) {
    this(delegate, true);
  }

  // visible for testing
  TransformSafeLoggerFactory(InternalLogger.Factory delegate, boolean enableMessageQueue) {
    this.delegate = delegate;
    this.enableMessageQueue = enableMessageQueue;
  }

  @Override
  public InternalLogger create(String name) {
    return new TransformSafeLogger(delegate, name, enableMessageQueue);
  }
}
