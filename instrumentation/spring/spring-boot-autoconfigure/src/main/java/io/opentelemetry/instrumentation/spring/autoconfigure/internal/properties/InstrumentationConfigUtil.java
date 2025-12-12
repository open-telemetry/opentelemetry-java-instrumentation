/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.function.Function;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumentationConfigUtil {
  private InstrumentationConfigUtil() {}

  @CanIgnoreReturnValue
  public static <T, REQUEST, RESPONSE> T configureClientBuilder(
      OpenTelemetry openTelemetry,
      T builder,
      Function<T, DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
    getBuilder.apply(builder).configure(openTelemetry);
    return builder;
  }

  @CanIgnoreReturnValue
  public static <T, REQUEST, RESPONSE> T configureServerBuilder(
      OpenTelemetry openTelemetry,
      T builder,
      Function<T, DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
    getBuilder.apply(builder).configure(openTelemetry);
    return builder;
  }

  /**
   * Check if statement sanitization is enabled for a specific instrumentation.
   *
   * @param openTelemetry the OpenTelemetry instance
   * @param instrumentationName the name of the instrumentation (e.g., "jdbc", "r2dbc", "mongo")
   * @return true if statement sanitization is enabled
   */
  public static boolean isStatementSanitizationEnabled(
      OpenTelemetry openTelemetry, String instrumentationName) {
    // Check instrumentation-specific setting first
    return DeclarativeConfigUtil.getBoolean(
            openTelemetry, instrumentationName, "statement_sanitizer", "enabled")
        // Fall back to common setting
        .or(
            () ->
                DeclarativeConfigUtil.getBoolean(
                    openTelemetry, "common", "db_statement_sanitizer", "enabled"))
        .orElse(true);
  }
}
