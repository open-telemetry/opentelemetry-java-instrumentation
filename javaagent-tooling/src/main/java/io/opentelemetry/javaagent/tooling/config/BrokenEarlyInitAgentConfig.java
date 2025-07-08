/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import javax.annotation.Nullable;

public final class BrokenEarlyInitAgentConfig implements EarlyInitAgentConfig {

  BrokenEarlyInitAgentConfig() {}

  @Override
  public boolean isAgentEnabled() {
    return false;
  }

  @Nullable
  @Override
  public String getString(String propertyName) {
    return null;
  }

  @Override
  public boolean getBoolean(String propertyName, boolean defaultValue) {
    return defaultValue;
  }

  @Override
  public int getInt(String propertyName, int defaultValue) {
    return defaultValue;
  }

  @Override
  public AutoConfiguredOpenTelemetrySdk installOpenTelemetrySdk(ClassLoader extensionClassLoader) {
    throw new IllegalStateException("agent is not enabled, cannot install OpenTelemetry SDK");
  }
}
