/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class LogbackSingletons {
  private static final boolean ADD_BAGGAGE =
      InstrumentationConfig.get().getBoolean("otel.instrumentation.logback-mdc.add-baggage", false);
  private static final String TRACE_ID_KEY = CommonConfig.get().getLoggingKeysTraceId();
  private static final String SPAN_ID_KEY = CommonConfig.get().getLoggingKeysSpanId();
  private static final String TRACE_FLAGS_KEY = CommonConfig.get().getLoggingKeysTraceFlags();

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
