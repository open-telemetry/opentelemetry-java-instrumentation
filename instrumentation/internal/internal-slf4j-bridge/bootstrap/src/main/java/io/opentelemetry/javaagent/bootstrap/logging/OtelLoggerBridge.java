/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.logging;

import io.opentelemetry.javaagent.OtelLogger;
import io.opentelemetry.javaagent.bootstrap.OtelLoggerHolder;

public class OtelLoggerBridge {
  private OtelLoggerBridge() {}

  public static void installSlf4jLogger(OtelLogger otelLogger) {
    OtelLoggerHolder.initialize(otelLogger);
  }
}
