/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.incubator.config.DeclarativeConfigException;
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
 * <p>For other declarative-config implementations, this utility expects duration values to be
 * integer milliseconds. Any other value, such as the string {@code 42ms}, is rejected.
 */
public final class DeclarativeConfigDurationUtil {

  /**
   * Reads a duration from declarative config.
   *
   * <p>String duration values are only supported when {@code properties} is a {@link
   * ConfigPropertiesBackedDeclarativeConfigProperties}. Other implementations must provide integer
   * milliseconds for the same key.
   *
   * @throws DeclarativeConfigException if {@code key} is set to a value that is not an integer
   *     number of milliseconds
   */
  @Nullable
  public static Duration getDuration(DeclarativeConfigProperties properties, String key) {
    if (properties instanceof ConfigPropertiesBackedDeclarativeConfigProperties) {
      return ((ConfigPropertiesBackedDeclarativeConfigProperties) properties).getDuration(key);
    }

    if (properties.isLong(key)) {
      return Duration.ofMillis(requireNonNull(properties.getLong(key)));
    }

    // Inspect only this key: converting the whole config to a map probes unrelated values.
    if (properties.isString(key)
        || properties.isBoolean(key)
        || properties.isDouble(key)
        || properties.getStructured(key) != null
        || properties.getStructuredList(key) != null
        || hasScalarList(properties, key)) {
      throw new DeclarativeConfigException(
          "Invalid duration for " + key + ": expected an integer number of milliseconds");
    }
    return null;
  }

  private static boolean hasScalarList(DeclarativeConfigProperties properties, String key) {
    // A list read can return null when none of its elements match the requested scalar type.
    return properties.getScalarList(key, String.class) != null
        || properties.getScalarList(key, Boolean.class) != null
        || properties.getScalarList(key, Long.class) != null
        || properties.getScalarList(key, Double.class) != null;
  }

  private DeclarativeConfigDurationUtil() {}
}
