/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class LogbackSingletons {
  private static final boolean ADD_BAGGAGE =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.logback-mdc.add-baggage", false);
  private static final String TRACE_ID_KEY =
      DeclarativeConfigUtil.getString(GlobalOpenTelemetry.get(), "general", "logging", "trace_id")
          .orElse(LoggingContextConstants.TRACE_ID);
  private static final String SPAN_ID_KEY =
      DeclarativeConfigUtil.getString(GlobalOpenTelemetry.get(), "general", "logging", "span_id")
          .orElse(LoggingContextConstants.SPAN_ID);
  private static final String TRACE_FLAGS_KEY =
      DeclarativeConfigUtil.getString(
              GlobalOpenTelemetry.get(), "general", "logging", "trace_flags")
          .orElse(LoggingContextConstants.TRACE_FLAGS);

  public static final VirtualField<ILoggingEvent, Context> CONTEXT =
      VirtualField.find(ILoggingEvent.class, Context.class);

  public static boolean addBaggage() {
    return ADD_BAGGAGE;
  }

  public static String traceIdKey() {
    return TRACE_ID_KEY;
  }

  public static String spanIdKey() {
    return SPAN_ID_KEY;
  }

  public static String traceFlagsKey() {
    return TRACE_FLAGS_KEY;
  }

  private LogbackSingletons() {}
}
