/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata.v2_17.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.LibraryConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextDataKeys {
  public static final String TRACE_ID_KEY =
      getLogging().getString("trace_id", LoggingContextConstants.TRACE_ID);

  public static final String SPAN_ID_KEY =
      getLogging().getString("span_id", LoggingContextConstants.SPAN_ID);

  public static final String TRACE_FLAGS_KEY =
      getLogging().getString("trace_flags", LoggingContextConstants.TRACE_FLAGS);

  private static DeclarativeConfigProperties getLogging() {
    return LibraryConfigUtil.getJavaInstrumentationConfig(GlobalOpenTelemetry.get(), "common")
        .get("logging");
  }

  private ContextDataKeys() {}
}
