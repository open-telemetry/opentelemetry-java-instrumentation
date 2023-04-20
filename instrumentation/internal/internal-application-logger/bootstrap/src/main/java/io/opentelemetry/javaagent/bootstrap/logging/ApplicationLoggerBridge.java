/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.logging;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ApplicationLoggerBridge {

  private static final AtomicReference<ApplicationLoggerBridge> applicationLoggerBridge =
      new AtomicReference<>();

  public static void set(ApplicationLoggerBridge bridge) {
    if (!applicationLoggerBridge.compareAndSet(null, bridge)) {
      throw new IllegalStateException(
          "ApplicationLoggerBridge was already set earlier."
              + " This should never happen in a properly build javaagent, and it's most likely a result of an error in the javaagent build.");
    }
  }

  public static void installApplicationLogger(InternalLogger.Factory applicationLoggerFactory) {
    ApplicationLoggerBridge bridge = applicationLoggerBridge.get();
    if (bridge == null) {
      throw new IllegalStateException(
          "ApplicationLoggerBridge#set() was not called before an attempt to install a bridge was made."
              + " This should never happen in a properly build javaagent, and it's most likely a result of an error in the javaagent build.");
    }
    bridge.install(applicationLoggerFactory);
  }

  protected abstract void install(InternalLogger.Factory applicationLoggerFactory);
}
