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

/**
 * Helpers for reading duration values from {@link DeclarativeConfigProperties}.
 *
 * <p>When the config is backed by flat {@link
 * io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties}, duration strings such as {@code 42ms}
 * are supported by delegating to the SDK's standard duration parser.
 *
 * <p>For other declarative-config implementations, this utility expects duration values to already
 * be normalized to integer milliseconds, which means string durations in declarative YAML are not
 * accepted.
 */
public final class DeclarativeConfigPropertiesDurationUtil {

  private DeclarativeConfigPropertiesDurationUtil() {}

  /**
   * Reads a duration from declarative config.
   *
   * <p>String duration values are only supported when {@code properties} is a {@link
   * ConfigPropertiesBackedDeclarativeConfigProperties}. Other implementations must provide integer
   * milliseconds for the same key.
   */
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
