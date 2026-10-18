/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import javax.annotation.Nullable;

/**
 * Helpers for instrumentation logger construction.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LoggerUtil {
  public static Logger createLogger(
      OpenTelemetry openTelemetry, String instrumentationName, @Nullable String schemaUrl) {
    LoggerBuilder builder = openTelemetry.getLogsBridge().loggerBuilder(instrumentationName);
    String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (version != null) {
      builder.setInstrumentationVersion(version);
    }
    if (schemaUrl != null) {
      builder.setSchemaUrl(schemaUrl);
    }
    return builder.build();
  }

  private LoggerUtil() {}
}
