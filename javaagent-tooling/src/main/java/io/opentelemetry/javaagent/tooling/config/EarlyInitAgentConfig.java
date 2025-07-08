/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import javax.annotation.Nullable;

public interface EarlyInitAgentConfig {
  boolean isAgentEnabled();

  @Nullable
  String getString(String propertyName);

  boolean getBoolean(String propertyName, boolean defaultValue);

  int getInt(String propertyName, int defaultValue);

  /**
   * Install the {@link OpenTelemetrySdk} using autoconfigure, and return the {@link
   * AutoConfiguredOpenTelemetrySdk}.
   *
   * @return the {@link AutoConfiguredOpenTelemetrySdk}
   */
  AutoConfiguredOpenTelemetrySdk installOpenTelemetrySdk(ClassLoader extensionClassLoader);

  static EarlyInitAgentConfig create() {
    String configurationFile =
        DefaultConfigProperties.create(Collections.emptyMap())
            .getString("otel.experimental.config.file");

    return configurationFile != null
        ? DeclarativeConfigEarlyInitAgentConfig.create(configurationFile)
        : new LegacyConfigFileEarlyInitAgentConfig();
  }
}
