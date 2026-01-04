/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public final class TracingHolder {

  private static final boolean CAPTURE_COMMAND_ENCODING_EVENTS =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "lettuce")
          .get("command_encoding_events/development")
          .getBoolean("enabled", false);

  private static final boolean CAPTURE_QUERY_PARAMETERS =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "lettuce")
          .getBoolean(
              "capture_query_parameters/development",
              ConfigPropertiesUtil.getBoolean(
                  "otel.instrumentation.jdbc.experimental.capture-query-parameters", false));

  public static final Tracing TRACING =
      LettuceTelemetry.builder(GlobalOpenTelemetry.get())
          .setStatementSanitizationEnabled(AgentCommonConfig.get().isStatementSanitizationEnabled())
          .setEncodingSpanEventsEnabled(CAPTURE_COMMAND_ENCODING_EVENTS)
          .setCaptureQueryParameters(CAPTURE_QUERY_PARAMETERS)
          .build()
          .newTracing();

  private TracingHolder() {}
}
