/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.util.Map;

/**
 * Agent config class that is only supposed to be used before the SDK (and {@link
 * io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties}) is initialized.
 */
public final class EarlyInitAgentConfig {

  public static EarlyInitAgentConfig create() {
    return new EarlyInitAgentConfig(ConfigurationFileLoader.getConfigFileContents());
  }

  private final Map<String, String> configFileContents;

  private EarlyInitAgentConfig(Map<String, String> configFileContents) {
    this.configFileContents = configFileContents;
  }

  public boolean getBoolean(String propertyName, boolean defaultValue) {
    String configFileValueStr = configFileContents.get(propertyName);
    boolean configFileValue =
        configFileValueStr == null ? defaultValue : Boolean.parseBoolean(configFileValueStr);
    return ConfigPropertiesUtil.getBoolean(propertyName, configFileValue);
  }
}
