/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata.v2_17.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextDataKeys {
  public static final String TRACE_ID_KEY =
      ConfigPropertiesUtil.getString(GlobalOpenTelemetry.get(), "common", "logging", "trace_id")
          .orElse(LoggingContextConstants.TRACE_ID);

  public static final String SPAN_ID_KEY =
      ConfigPropertiesUtil.getString(GlobalOpenTelemetry.get(), "common", "logging", "span_id")
          .orElse(LoggingContextConstants.SPAN_ID);

  public static final String TRACE_FLAGS_KEY =
      ConfigPropertiesUtil.getString(GlobalOpenTelemetry.get(), "common", "logging", "trace_flags")
          .orElse(LoggingContextConstants.TRACE_FLAGS);

  private ContextDataKeys() {}
}
