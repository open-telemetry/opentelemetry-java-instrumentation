/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.util.Collections.singletonMap;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.time.Duration;
import javax.annotation.Nullable;

public class DeclarativeConfigPropertiesDurationUtil {

  private DeclarativeConfigPropertiesDurationUtil() {}

  @Nullable
  public static Duration parseDuration(DeclarativeConfigProperties properties, String key) {
    if (properties instanceof ConfigPropertiesBackedDeclarativeConfigProperties) {
      String rawValue = properties.getString(key);
      if (rawValue == null || rawValue.isEmpty()) {
        return null;
      }
      return DefaultConfigProperties.createFromMap(singletonMap(key, rawValue)).getDuration(key);
    }

    Long rawLongValue = properties.getLong(key);
    if (rawLongValue == null) {
      return null;
    }
    return Duration.ofMillis(rawLongValue);
  }
}
