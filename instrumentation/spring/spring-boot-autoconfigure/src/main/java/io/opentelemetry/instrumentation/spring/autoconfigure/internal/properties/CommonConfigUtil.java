/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import io.opentelemetry.instrumentation.api.incubator.config.internal.CoreCommonConfig;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class CommonConfigUtil {
  private CommonConfigUtil() {}

  public static CoreCommonConfig getCommonConfig(ConfigProperties config) {
    return new CoreCommonConfig(new ConfigPropertiesBridge(config));
  }
}
