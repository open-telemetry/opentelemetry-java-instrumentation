/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
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
  public static <T, REQUEST, RESPONSE> T configureBuilder(
      ConfigProperties config,
      T builder,
      Function<T, DefaultHttpClientInstrumenterBuilder<REQUEST, RESPONSE>> getBuilder) {
    getBuilder.apply(builder).configure(new CommonConfig(new ConfigPropertiesBridge(config)));
    return builder;
  }
}
