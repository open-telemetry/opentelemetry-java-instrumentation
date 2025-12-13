/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata.v2_17.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.instrumentation.api.internal.ConfigProviderUtil;
import java.util.Optional;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextDataKeys {
  public static final String TRACE_ID_KEY = get("trace_id", LoggingContextConstants.TRACE_ID);

  public static final String SPAN_ID_KEY = get("span_id", LoggingContextConstants.SPAN_ID);

  public static final String TRACE_FLAGS_KEY =
      get("trace_flags", LoggingContextConstants.TRACE_FLAGS);

  private static String get(String traceId, String defaultValue) {
    return Optional.ofNullable(
            InstrumentationConfigUtil.getOrNull(
                ConfigProviderUtil.getConfigProvider(GlobalOpenTelemetry.get()),
                config -> config.getString(traceId),
                "java",
                "common",
                "logging"))
        .orElse(defaultValue);
  }

  private ContextDataKeys() {}
}
