/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
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
      InstrumentationConfig config,
      T builder,
      Function<T, DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
    getBuilder.apply(builder).configure(openTelemetry);
    return builder;
  }

  @CanIgnoreReturnValue
  public static <T, REQUEST, RESPONSE> T configureServerBuilder(
      OpenTelemetry openTelemetry,
      InstrumentationConfig config,
      T builder,
      Function<T, DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
    getBuilder.apply(builder).configure(openTelemetry);
    return builder;
  }

  public static boolean isStatementSanitizationEnabled(InstrumentationConfig config, String key) {
    return config.getBoolean(
        key, config.getBoolean("otel.instrumentation.common.db-statement-sanitizer.enabled", true));
  }
}
