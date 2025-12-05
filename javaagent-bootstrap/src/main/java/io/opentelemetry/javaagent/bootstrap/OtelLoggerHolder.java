/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.OtelLogger;
import java.util.concurrent.atomic.AtomicReference;

public final class OtelLoggerHolder {

  private static final OtelLogger NOOP_OTEL_LOGGER = (unused, unused2, unused3, unused4, unused5, unused6) -> {};

  private static final AtomicReference<OtelLogger> otelLogger =
      new AtomicReference<>(NOOP_OTEL_LOGGER);

  public static void initialize(OtelLogger otelLogger) {
    if (!OtelLoggerHolder.otelLogger.compareAndSet(NOOP_OTEL_LOGGER, otelLogger)) {
      otelLogger
          .record(
              Context.root(),
              OtelLogger.class.getName(),
              null,
              Value.of("Developer error: logging system has already been initialized once"),
              Attributes.empty(),
              Severity.WARN);
    }
  }

  public static OtelLogger get() {
    return otelLogger.get();
  }

  private OtelLoggerHolder() {}
}
