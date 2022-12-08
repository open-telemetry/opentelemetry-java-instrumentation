/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.util.concurrent.atomic.AtomicReference;

public abstract class ApplicationLoggerBridge {

  private static final AtomicReference<ApplicationLoggerBridge> applicationLoggerBridge =
      new AtomicReference<>();

  public static void set(ApplicationLoggerBridge bridge) {
    applicationLoggerBridge.compareAndSet(null, bridge);
  }

  public static void installApplicationLogger(InternalLogger.Factory applicationLoggerFactory) {
    ApplicationLoggerBridge bridge = applicationLoggerBridge.get();
    if (bridge != null) {
      bridge.install(applicationLoggerFactory);
    }
  }

  protected abstract void install(InternalLogger.Factory applicationLoggerFactory);
}
