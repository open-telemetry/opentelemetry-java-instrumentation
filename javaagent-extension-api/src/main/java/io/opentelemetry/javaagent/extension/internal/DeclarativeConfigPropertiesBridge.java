/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * A {@link ConfigProperties} which resolves properties based on {@link
 * DeclarativeConfigProperties}.
 *
 * <p>Only properties starting with "otel.instrumentation." are resolved. Others return null (or
 * default value if provided).
 *
 * <p>To resolve:
 *
 * <ul>
 *   <li>"otel.instrumentation" refers to the ".instrumentation.java" node
 *   <li>The portion of the property after "otel.instrumentation." is split into segments based on
 *       ".".
 *   <li>For each N-1 segment, we walk down the tree to find the relevant leaf {@link
 *       DeclarativeConfigProperties}.
 *   <li>We extract the property from the resolved {@link DeclarativeConfigProperties} using the
 *       last segment as the property key.
 * </ul>
 *
 * <p>For example, given the following YAML, asking for {@code
 * ConfigProperties#getString("otel.instrumentation.common.string_key")} yields "value":
 *
 * <pre>
 *   instrumentation:
 *     java:
 *       common:
 *         string_key: value
 * </pre>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
final class DeclarativeConfigPropertiesBridge implements ConfigProperties {

  private static final String OTEL_INSTRUMENTATION_PREFIX = "otel.instrumentation.";

  private final ConfigPropertiesTranslator translator;
  @Nullable private final DeclarativeConfigProperties baseNode;

  DeclarativeConfigPropertiesBridge(
      @Nullable DeclarativeConfigProperties baseNode, ConfigPropertiesTranslator translator) {
    this.baseNode = baseNode;
    this.translator = translator;
  }

  @Nullable
  @Override
  public String getString(String propertyName) {
    Object value = translator.get(propertyName);
    if (value != null) {
      return value.toString();
    }
    return getPropertyValue(propertyName, DeclarativeConfigProperties::getString);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String propertyName) {
    Object value = translator.get(propertyName);
    if (value != null) {
      return (Boolean) value;
    }
    return getPropertyValue(propertyName, DeclarativeConfigProperties::getBoolean);
  }

  @Nullable
  @Override
  public Integer getInt(String propertyName) {
    Object value = translator.get(propertyName);
    if (value != null) {
      return (Integer) value;
    }
    return getPropertyValue(propertyName, DeclarativeConfigProperties::getInt);
  }

  @Nullable
  @Override
  public Long getLong(String propertyName) {
    Object value = translator.get(propertyName);
    if (value != null) {
      return (Long) value;
    }
    return getPropertyValue(propertyName, DeclarativeConfigProperties::getLong);
  }

  @Nullable
  @Override
  public Double getDouble(String propertyName) {
    Object value = translator.get(propertyName);
    if (value != null) {
      return (Double) value;
    }
    return getPropertyValue(propertyName, DeclarativeConfigProperties::getDouble);
  }

  @Nullable
  @Override
  public Duration getDuration(String propertyName) {
    Object value = translator.get(propertyName);
    if (value != null) {
      return (Duration) value;
    }
    Long millis = getPropertyValue(propertyName, DeclarativeConfigProperties::getLong);
    if (millis == null) {
      return null;
    }
    return Duration.ofMillis(millis);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getList(String propertyName) {
    Object value = translator.get(propertyName);
    if (value != null) {
      return (List<String>) value;
    }
    List<String> propertyValue =
        getPropertyValue(
            propertyName,
            (properties, lastPart) -> properties.getScalarList(lastPart, String.class));
    return propertyValue == null ? Collections.emptyList() : propertyValue;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, String> getMap(String propertyName) {
    Object fixed = translator.get(propertyName);
    if (fixed != null) {
      return (Map<String, String>) fixed;
    }
    DeclarativeConfigProperties propertyValue =
        getPropertyValue(propertyName, DeclarativeConfigProperties::getStructured);
    if (propertyValue == null) {
      return Collections.emptyMap();
    }
    Map<String, String> result = new HashMap<>();
    propertyValue
        .getPropertyKeys()
        .forEach(
            key -> {
              String value = propertyValue.getString(key);
              if (value == null) {
                return;
              }
              result.put(key, value);
            });
    return Collections.unmodifiableMap(result);
  }

  @Nullable
  private <T> T getPropertyValue(
      String property, BiFunction<DeclarativeConfigProperties, String, T> extractor) {
    if (baseNode == null) {
      return null;
    }

    String[] segments = getSegments(translator.translateProperty(property));
    if (segments.length == 0) {
      return null;
    }

    // Extract the value by walking to the N-1 entry
    DeclarativeConfigProperties target = baseNode;
    if (segments.length > 1) {
      for (int i = 0; i < segments.length - 1; i++) {
        target = target.getStructured(segments[i], empty());
      }
    }
    String lastPart = segments[segments.length - 1];

    return extractor.apply(target, lastPart);
  }

  private static String[] getSegments(String property) {
    if (property.startsWith(OTEL_INSTRUMENTATION_PREFIX)) {
      property = property.substring(OTEL_INSTRUMENTATION_PREFIX.length());
    }
    // Split the remainder of the property on "."
    return property.replace('-', '_').split("\\.");
  }
}
