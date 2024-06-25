/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.function.Function;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InstrumentationConfigUtil {
  private InstrumentationConfigUtil() {}

  @CanIgnoreReturnValue
  public static <T, REQUEST, RESPONSE> T configureClientAndServerBuilder(ConfigProperties config, T builder) {
    DefaultHttpClientInstrumenterBuilder.unwrapAndConfigure(
        new CoreCommonConfig(new ConfigPropertiesBridge(config)), builder);
    DefaultHttpServerInstrumenterBuilder.unwrapAndConfigure(
        new CoreCommonConfig(new ConfigPropertiesBridge(config)), builder);
    return builder;
  }

  @CanIgnoreReturnValue
  public static <T> T configureClientBuilder(
      ConfigProperties config,
      T builder,
      Function<T, DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
    getBuilder.apply(builder).configure(new CommonConfig(new ConfigPropertiesBridge(config)));
    return builder;
  }

  @CanIgnoreReturnValue
  public static <T> T configureServerBuilder(ConfigProperties config, T builder) {
    DefaultHttpServerInstrumenterBuilder.unwrapAndConfigure(
        new CoreCommonConfig(new ConfigPropertiesBridge(config)), builder);
    return builder;
  }

  public static boolean isStatementSanitizationEnabled(ConfigProperties config, String key) {
    return config.getBoolean(
        key, config.getBoolean("otel.instrumentation.common.db-statement-sanitizer.enabled", true));
  }
}
