/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.sdk.config.bridge;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A builder for {@link DeclarativeConfigPropertiesBridge} that allows adding translations and fixed
 * values for properties.
 */
public class DeclarativeConfigPropertiesBridgeBuilder {
  /**
   * order is important here, so we use LinkedHashMap - see {@link #addMapping(String, String)} for
   * more details
   */
  private final Map<String, String> mappings = new LinkedHashMap<>();

  private final Map<String, Object> overrideValues = new HashMap<>();

  public DeclarativeConfigPropertiesBridgeBuilder() {}

  /**
   * Adds a mapping from a property prefix to a YAML path.
   *
   * <p>For example, if the property prefix is "otel.javaagent" and the YAML path is "agent", then
   * any property starting with "otel.javaagent." will be resolved against the "agent" node in the
   * instrumentation/java section of the YAML configuration.
   *
   * @param propertyPrefix the prefix of the property to translate
   * @param yamlPath the YAML path to resolve the property against
   */
  @CanIgnoreReturnValue
  public DeclarativeConfigPropertiesBridgeBuilder addMapping(
      String propertyPrefix, String yamlPath) {
    mappings.put(propertyPrefix, yamlPath);
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

  /** Build {@link ConfigProperties} from the {@code autoConfiguredOpenTelemetrySdk}. */
  public ConfigProperties build(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ConfigProperties sdkConfigProperties =
        AutoConfigureUtil.getConfig(autoConfiguredOpenTelemetrySdk);
    if (sdkConfigProperties != null) {
      return sdkConfigProperties;
    }
    ConfigProvider configProvider =
        AutoConfigureUtil.getConfigProvider(autoConfiguredOpenTelemetrySdk);
    if (configProvider != null) {
      return buildFromInstrumentationConfig(configProvider.getInstrumentationConfig());
    }
    // Should never happen
    throw new IllegalStateException(
        "AutoConfiguredOpenTelemetrySdk does not have ConfigProperties or DeclarativeConfigProperties. This is likely a programming error in opentelemetry-java");
  }

  /**
   * Build {@link ConfigProperties} from the provided {@link DeclarativeConfigProperties} node.
   *
   * @param node the declarative config properties to build from
   * @return a new instance of {@link ConfigProperties}
   */
  public ConfigProperties build(@Nullable DeclarativeConfigProperties node) {
    return new DeclarativeConfigPropertiesBridge(
        node == null ? empty() : node, mappings, overrideValues);
  }

  /**
   * Build {@link ConfigProperties} from the {@link DeclarativeConfigProperties} provided by the
   * instrumentation configuration.
   *
   * <p>If the provided {@code instrumentationConfig} is null, an empty {@link
   * DeclarativeConfigProperties} will be used.
   *
   * @param instrumentationConfig the instrumentation configuration to build from
   * @return a new instance of {@link ConfigProperties}
   */
  public ConfigProperties buildFromInstrumentationConfig(
      @Nullable DeclarativeConfigProperties instrumentationConfig) {
    return build(
        instrumentationConfig == null ? null : instrumentationConfig.getStructured("java"));
  }
}
