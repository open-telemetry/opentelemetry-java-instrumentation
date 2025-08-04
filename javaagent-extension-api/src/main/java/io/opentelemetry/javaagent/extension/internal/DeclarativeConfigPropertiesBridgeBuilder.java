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
 * A builder for {@link DeclarativeConfigPropertiesBridge} that allows adding translations and fixed
 * values for properties.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class DeclarativeConfigPropertiesBridgeBuilder {
  private final LinkedHashMap<String, String> translationMap = new LinkedHashMap<>();
  private final Map<String, Object> overrideValues = new LinkedHashMap<>();

  public DeclarativeConfigPropertiesBridgeBuilder() {}

  /**
   * Adds a translation from a property prefix to a YAML path.
   *
   * <p>For example, if the property prefix is "otel.javaagent" and the YAML path is "agent", then
   * any property starting with "otel.javaagent." will be resolved against the "agent" node in the
   * instrumentation/java section of the YAML configuration.
   *
   * @param propertyPrefix the prefix of the property to translate
   * @param yamlPath the YAML path to resolve the property against
   */
  @CanIgnoreReturnValue
  public DeclarativeConfigPropertiesBridgeBuilder addTranslation(
      String propertyPrefix, String yamlPath) {
    translationMap.put(propertyPrefix, yamlPath);
    return this;
  }

  /**
   * Adds a fixed override value for a property.
   *
   * @param propertyName the name of the property to override
   * @param value the value to return when the property is requested
   */
  @CanIgnoreReturnValue
  public DeclarativeConfigPropertiesBridgeBuilder addOverride(String propertyName, Object value) {
    overrideValues.put(propertyName, value);
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
    return new DeclarativeConfigPropertiesBridge(
        instrumentationConfig.getStructured("java", empty()), translationMap, overrideValues);
  }
}
