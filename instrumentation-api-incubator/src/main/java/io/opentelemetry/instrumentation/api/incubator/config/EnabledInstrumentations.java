/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config;

import javax.annotation.Nullable;

/** Provides information about which instrumentations are enabled. */
public interface EnabledInstrumentations {
  /**
   * Returns whether the given instrumentation is enabled.
   *
   * @param instrumentationName the name of the instrumentation
   * @return {@code Boolean.TRUE} if the instrumentation is enabled, {@code Boolean.FALSE} if it is
   *     disabled, or {@code null} if the default setting should be used
   */
  @Nullable
  Boolean getEnabled(String instrumentationName);

  /**
   * Returns whether instrumentations are enabled by default.
   *
   * @return {@code true} if instrumentations are enabled by default, {@code false} otherwise
   */
  boolean isDefaultEnabled();

  /**
   * Returns whether the given instrumentation is explicitly enabled (i.e., not relying on the
   * default setting).
   *
   * @param instrumentationName the name of the instrumentation
   * @return {@code true} if the instrumentation is explicitly enabled, {@code false} otherwise
   */
  default boolean isEnabledExplicitly(String instrumentationName) {
    return Boolean.TRUE.equals(getEnabled(instrumentationName));
  }

  /**
   * Returns whether the given instrumentation is enabled, falling back to the default setting if
   * not explicitly configured.
   *
   * @param instrumentationName the name of the instrumentation
   * @return {@code true} if the instrumentation is enabled, {@code false} otherwise
   */
  default boolean isEnabled(String instrumentationName) {
    Boolean enabled = getEnabled(instrumentationName);
    if (enabled != null) {
      return enabled;
    }
    return isDefaultEnabled();
  }
}
