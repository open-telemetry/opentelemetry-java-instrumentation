/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config;

import javax.annotation.Nullable;

/**
 * Listener interface for option value changes.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface OptionChangeListener {

  /**
   * Called when the value of an option changes.
   *
   * @param key the option key
   * @param newValue the new value, or {@code null} if the option was removed
   * @param oldValue the previous value, or {@code null} if the option was not set
   */
  void onOptionChanged(String key, @Nullable String newValue, @Nullable String oldValue);
}
