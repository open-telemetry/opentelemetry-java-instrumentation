/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A builder for {@link DeclarativeConfigPropertiesBridge} that allows adding translations and fixed
 * values for properties.
 */
class DeclarativeConfigPropertiesBridgeBuilder {
  /**
   * order is important here, so we use LinkedHashMap - see {@link #addMapping(String, String)} for
   * more details
   */
  private final Map<String, String> mappings = new LinkedHashMap<>();

  private final Map<String, Object> overrideValues = new HashMap<>();

  DeclarativeConfigPropertiesBridgeBuilder() {}

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
  DeclarativeConfigPropertiesBridgeBuilder addMapping(String propertyPrefix, String yamlPath) {
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
  DeclarativeConfigPropertiesBridgeBuilder addOverride(String propertyName, Object value) {
    overrideValues.put(propertyName, value);
    return this;
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
  ConfigProperties buildFromInstrumentationConfig(
      @Nullable DeclarativeConfigProperties instrumentationConfig) {
    DeclarativeConfigProperties javaConfig =
        instrumentationConfig == null ? null : instrumentationConfig.getStructured("java");
    return new DeclarativeConfigPropertiesBridge(
        javaConfig == null ? empty() : javaConfig, mappings, overrideValues);
  }
}
