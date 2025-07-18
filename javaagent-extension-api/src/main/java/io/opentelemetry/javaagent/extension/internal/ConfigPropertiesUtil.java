/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.internal;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ConfigPropertiesUtil {
  private ConfigPropertiesUtil() {}

  /** Resolve {@link ConfigProperties} from the {@code autoConfiguredOpenTelemetrySdk}. */
  public static ConfigProperties resolveConfigProperties(
      AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ConfigProperties sdkConfigProperties =
        AutoConfigureUtil.getConfig(autoConfiguredOpenTelemetrySdk);
    if (sdkConfigProperties != null) {
      return sdkConfigProperties;
    }
    ConfigProvider configProvider =
        AutoConfigureUtil.getConfigProvider(autoConfiguredOpenTelemetrySdk);
    if (configProvider != null) {
      return resolveInstrumentationConfig(
          configProvider.getInstrumentationConfig(), propertyTranslatorBuilder());
    }
    // Should never happen
    throw new IllegalStateException(
        "AutoConfiguredOpenTelemetrySdk does not have ConfigProperties or DeclarativeConfigProperties. This is likely a programming error in opentelemetry-java");
  }

  public static ConfigProperties resolveInstrumentationConfig(
      @Nullable DeclarativeConfigProperties instrumentationConfig) {
    return resolveInstrumentationConfig(instrumentationConfig, propertyTranslatorBuilder());
  }

  public static ConfigProperties resolveInstrumentationConfig(
      @Nullable DeclarativeConfigProperties instrumentationConfig,
      PropertyTranslatorBuilder builder) {
    return DeclarativeConfigPropertiesBridge.fromInstrumentationConfig(
        instrumentationConfig, builder.build());
  }

  public static ConfigProperties resolveConfig(
      @Nullable DeclarativeConfigProperties config, PropertyTranslatorBuilder builder) {
    return DeclarativeConfigPropertiesBridge.create(config, builder.build());
  }

  public static String propertyYamlPath(String propertyName) {
    return DeclarativeConfigPropertiesBridge.yamlPath(propertyName);
  }

  public static PropertyTranslatorBuilder propertyTranslatorBuilder() {
    return new PropertyTranslatorBuilder();
  }
}
