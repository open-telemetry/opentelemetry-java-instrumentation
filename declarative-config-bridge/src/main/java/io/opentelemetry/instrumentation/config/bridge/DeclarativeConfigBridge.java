/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/** Exposes flat {@link ConfigProperties} through the declarative configuration APIs. */
public final class DeclarativeConfigBridge {

  /**
   * Creates the complete declarative instrumentation configuration backed by flat {@link
   * ConfigProperties}.
   */
  public static ConfigProvider createInstrumentationConfig(ConfigProperties configProperties) {
    return createConfigProvider(
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            configProperties));
  }

  /**
   * Creates component-local declarative properties backed by flat {@link ConfigProperties}.
   *
   * <p>The returned properties have the same root-relative shape as the properties passed to a
   * declarative {@code ComponentProvider}. For example, a component can read {@code enabled} while
   * the bridge resolves the value from {@code configPropertyPrefix + "enabled"}.
   */
  public static DeclarativeConfigProperties createComponentProperties(
      ConfigProperties configProperties, String configPropertyPrefix) {
    return ConfigPropertiesBackedDeclarativeConfigProperties.createComponentProperties(
        configProperties, configPropertyPrefix);
  }

  static ConfigProvider createConfigProvider(DeclarativeConfigProperties instrumentationConfig) {
    return new BridgedConfigProvider(instrumentationConfig);
  }

  private DeclarativeConfigBridge() {}

  private static final class BridgedConfigProvider implements ConfigProvider {
    private final DeclarativeConfigProperties instrumentationConfig;

    private BridgedConfigProvider(DeclarativeConfigProperties instrumentationConfig) {
      this.instrumentationConfig = instrumentationConfig;
    }

    @Override
    public DeclarativeConfigProperties getInstrumentationConfig() {
      return instrumentationConfig;
    }
  }
}
