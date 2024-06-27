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
  public static <T, CLIENT_REQUEST, CLIENT_RESPONSE, SERVER_REQUEST, SERVER_RESPONSE>
      T configureClientAndServerBuilder(
          ConfigProperties config,
          T builder,
          Function<T, DefaultHttpClientInstrumenterBuilder<CLIENT_REQUEST, CLIENT_RESPONSE>>
              getClientBuilder,
          Function<T, DefaultHttpServerInstrumenterBuilder<SERVER_REQUEST, SERVER_RESPONSE>>
              getServerBuilder) {
    CoreCommonConfig commonConfig = getConfig(config);
    getClientBuilder.apply(builder).configure(commonConfig);
    getServerBuilder.apply(builder).configure(commonConfig);
    return builder;
  }

  @CanIgnoreReturnValue
  public static <T, REQUEST, RESPONSE> T configureClientBuilder(
      ConfigProperties config,
      T builder,
      Function<T, DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
    getBuilder.apply(builder).configure(getConfig(config));
    return builder;
  }

  @CanIgnoreReturnValue
  public static <T, REQUEST, RESPONSE> T configureServerBuilder(
      ConfigProperties config,
      T builder,
      Function<T, DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
    getBuilder.apply(builder).configure(getConfig(config));
    return builder;
  }

  private static CoreCommonConfig getConfig(ConfigProperties config) {
    return new CoreCommonConfig(new ConfigPropertiesBridge(config));
  }

  public static boolean isStatementSanitizationEnabled(ConfigProperties config, String key) {
    return config.getBoolean(
        key, config.getBoolean("otel.instrumentation.common.db-statement-sanitizer.enabled", true));
  }
}
