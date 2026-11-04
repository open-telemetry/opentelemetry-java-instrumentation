/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.logging;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;

public final class LoggerInitializer {

  public static void initialize(InternalLogger.Factory factory) {
    InternalLogger.initialize(new TransformSafeLoggerFactory(factory));
  }

  private LoggerInitializer() {}
}
