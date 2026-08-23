/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
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
public final class DeclarativeConfigDurationUtil {

  /**
   * Reads a duration from declarative config.
   *
   * <p>String duration values are only supported when {@code properties} is a {@link
   * ConfigPropertiesBackedDeclarativeConfigProperties}. Other implementations must provide integer
   * milliseconds for the same key.
   */
  @Nullable
  public static Duration getDuration(DeclarativeConfigProperties properties, String key) {
    if (properties instanceof ConfigPropertiesBackedDeclarativeConfigProperties) {
      return ((ConfigPropertiesBackedDeclarativeConfigProperties) properties).getDuration(key);
    }

    Long rawLongValue = properties.getLong(key);
    if (rawLongValue == null) {
      return null;
    }
    return Duration.ofMillis(rawLongValue);
  }

  private DeclarativeConfigDurationUtil() {}
}
