/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public class LogbackSingletons {
  public static final boolean ADD_BAGGAGE =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "logback_mdc")
          .getBoolean("add_baggage", false);
  public static final String TRACE_ID_KEY = AgentCommonConfig.get().getTraceIdKey();
  public static final String SPAN_ID_KEY = AgentCommonConfig.get().getSpanIdKey();
  public static final String TRACE_FLAGS_KEY = AgentCommonConfig.get().getTraceFlagsKey();

  public static final VirtualField<ILoggingEvent, Context> CONTEXT =
      VirtualField.find(ILoggingEvent.class, Context.class);

  private LogbackSingletons() {}
}
