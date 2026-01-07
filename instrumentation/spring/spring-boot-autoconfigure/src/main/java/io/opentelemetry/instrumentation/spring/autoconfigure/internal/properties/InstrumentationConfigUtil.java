/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
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
    getBuilder.apply(builder).configure(getConfig(openTelemetry));
    return builder;
  }

  @CanIgnoreReturnValue
  public static <T, REQUEST, RESPONSE> T configureServerBuilder(
      OpenTelemetry openTelemetry,
      T builder,
      Function<T, DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
    getBuilder.apply(builder).configure(getConfig(openTelemetry));
    return builder;
  }

  private static CommonConfig getConfig(OpenTelemetry openTelemetry) {
    return new CommonConfig(openTelemetry);
  }

  public static boolean isStatementSanitizationEnabled(
      OpenTelemetry openTelemetry, String instrumentationName) {
    ExtendedDeclarativeConfigProperties instrumentationConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, instrumentationName);
    ExtendedDeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");
    return instrumentationConfig
        .get("statement_sanitizer")
        .getBoolean(
            "enabled",
            commonConfig.get("database").get("statement_sanitizer").getBoolean("enabled", true));
  }
}
