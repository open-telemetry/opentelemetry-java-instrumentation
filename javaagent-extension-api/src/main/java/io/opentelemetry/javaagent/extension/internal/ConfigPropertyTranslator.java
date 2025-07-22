/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("NonApiType")
public class ConfigPropertyTranslator {
  // lookup order matters - we choose the first match
  private final LinkedHashMap<String, String> translationMap;
  private final Map<String, Object> fixedValues;

  public static Builder builder() {
    return new Builder();
  }

  ConfigPropertyTranslator(
      LinkedHashMap<String, String> translationMap, Map<String, Object> fixedValues) {
    this.translationMap = translationMap;
    this.fixedValues = fixedValues;
  }

  String translateProperty(String property) {
    for (Map.Entry<String, String> entry : translationMap.entrySet()) {
      if (property.startsWith(entry.getKey())) {
        return entry.getValue() + property.substring(entry.getKey().length());
      }
    }
    return property;
  }

  public Object get(String propertyName) {
    return fixedValues.get(propertyName);
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Builder {
    private final LinkedHashMap<String, String> translationMap = new LinkedHashMap<>();
    private final Map<String, Object> fixedValues = new LinkedHashMap<>();

    private Builder() {}

    @CanIgnoreReturnValue
    public Builder addTranslation(String propertyName, String yamlPath) {
      translationMap.put(propertyName, yamlPath);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addFixedValue(String propertyName, Object value) {
      fixedValues.put(propertyName, value);
      return this;
    }

    /** Resolve {@link ConfigProperties} from the {@code autoConfiguredOpenTelemetrySdk}. */
    public ConfigProperties resolveConfigProperties(
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
      ConfigProperties sdkConfigProperties =
          AutoConfigureUtil.getConfig(autoConfiguredOpenTelemetrySdk);
      if (sdkConfigProperties != null) {
        return sdkConfigProperties;
      }
      ConfigProvider configProvider =
          AutoConfigureUtil.getConfigProvider(autoConfiguredOpenTelemetrySdk);
      if (configProvider != null) {
        return resolveInstrumentationConfig(configProvider.getInstrumentationConfig());
      }
      // Should never happen
      throw new IllegalStateException(
          "AutoConfiguredOpenTelemetrySdk does not have ConfigProperties or DeclarativeConfigProperties. This is likely a programming error in opentelemetry-java");
    }

    public ConfigProperties resolveInstrumentationConfig(
        @Nullable DeclarativeConfigProperties instrumentationConfig) {
      if (instrumentationConfig == null) {
        instrumentationConfig = DeclarativeConfigProperties.empty();
      }
      return resolveConfig(instrumentationConfig.getStructured("java", empty()));
    }

    public ConfigProperties resolveConfig(@Nullable DeclarativeConfigProperties config) {
      return new DeclarativeConfigPropertiesBridge(
          config, new ConfigPropertyTranslator(translationMap, fixedValues));
    }
  }
}
