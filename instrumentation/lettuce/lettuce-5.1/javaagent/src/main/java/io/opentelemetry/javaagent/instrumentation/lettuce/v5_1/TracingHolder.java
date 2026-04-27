/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;

public class TracingHolder {

  private static final boolean CAPTURE_COMMAND_ENCODING_EVENTS =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "lettuce")
          .get("command_encoding_events/development")
          .getBoolean("enabled", false);

  private static final Tracing tracing =
      LettuceTelemetry.builder(GlobalOpenTelemetry.get())
          .setQuerySanitizationEnabled(
              DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "lettuce"))
          .setEncodingSpanEventsEnabled(CAPTURE_COMMAND_ENCODING_EVENTS)
          .build()
          .createTracing();

  public static Tracing getTracing() {
    return tracing;
  }

  private TracingHolder() {}
}
