/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CoreCommonConfig;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InstrumentationConfigUtil {
  private InstrumentationConfigUtil() {}

  public static <T> T configureBuilder(ConfigProperties config, T builder) {
    DefaultHttpClientInstrumenterBuilder.unwrapAndConfigure(
        new CoreCommonConfig(new ConfigPropertiesBridge(config)), builder);
    return builder;
  }
}
