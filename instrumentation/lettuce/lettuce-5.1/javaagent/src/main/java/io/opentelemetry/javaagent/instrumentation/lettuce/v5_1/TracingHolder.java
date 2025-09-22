/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class TracingHolder {

  private static final boolean CAPTURE_ENCODING_SPAN_EVENTS =
      AgentInstrumentationConfig.get()
          .getBoolean(
              "otel.instrumentation.lettuce.experimental.encoding-span-events.enabled", true);

  public static final Tracing TRACING =
      LettuceTelemetry.builder(GlobalOpenTelemetry.get())
          .setStatementSanitizationEnabled(AgentCommonConfig.get().isStatementSanitizationEnabled())
          .setEncodingSpanEventsEnabled(CAPTURE_ENCODING_SPAN_EVENTS)
          .build()
          .newTracing();

  private TracingHolder() {}
}
