/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.Slf4jLogRecorder;
import java.util.concurrent.atomic.AtomicReference;

public final class Slf4jBridgeLogRecorderHolder {

  private static final Slf4jLogRecorder NOOP_OTEL_LOGGER =
      (unused, unused2, unused3, unused4, unused5, unused6) -> {};

  private static final AtomicReference<Slf4jLogRecorder> otelLogger =
      new AtomicReference<>(NOOP_OTEL_LOGGER);

  public static void initialize(Slf4jLogRecorder slf4JLogRecorder) {
    if (!Slf4jBridgeLogRecorderHolder.otelLogger.compareAndSet(
        NOOP_OTEL_LOGGER, slf4JLogRecorder)) {
      slf4JLogRecorder.record(
          Context.root(),
          Slf4jLogRecorder.class.getName(),
          null,
          Value.of("Developer error: logging system has already been initialized once"),
          Attributes.empty(),
          Severity.WARN);
    }
  }

  public static Slf4jLogRecorder get() {
    return otelLogger.get();
  }

  private Slf4jBridgeLogRecorderHolder() {}
}
