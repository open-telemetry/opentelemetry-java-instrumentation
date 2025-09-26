/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

public class SimpleDeclarativeConfigPropertiesBridge {
  private static final String OTEL_INSTRUMENTATION_PREFIX = "otel.instrumentation.";
  protected final DeclarativeConfigProperties baseNode;
  // lookup order matters - we choose the first match
  protected final Map<String, String> mappings;
  protected final Map<String, Object> overrideValues;

  public SimpleDeclarativeConfigPropertiesBridge(
      DeclarativeConfigProperties baseNode,
      Map<String, String> mappings,
      Map<String, Object> overrideValues) {
    this.baseNode = Objects.requireNonNull(baseNode);
    this.mappings = mappings;
    this.overrideValues = overrideValues;
  }

  @Nullable
  public String getString(String propertyName) {
    return getPropertyValue(propertyName, String.class, DeclarativeConfigProperties::getString);
  }

  @Nullable
  public Boolean getBoolean(String propertyName) {
    return getPropertyValue(propertyName, Boolean.class, DeclarativeConfigProperties::getBoolean);
  }

  @Nullable
  public Integer getInt(String propertyName) {
    return getPropertyValue(propertyName, Integer.class, DeclarativeConfigProperties::getInt);
  }

  @Nullable
  public Long getLong(String propertyName) {
    return getPropertyValue(propertyName, Long.class, DeclarativeConfigProperties::getLong);
  }

  @Nullable
  public Double getDouble(String propertyName) {
    return getPropertyValue(propertyName, Double.class, DeclarativeConfigProperties::getDouble);
  }

  @Nullable
  public Duration getDuration(String propertyName) {
    Long millis = getPropertyValue(propertyName, Long.class, DeclarativeConfigProperties::getLong);
    if (millis == null) {
      return null;
    }
    return Duration.ofMillis(millis);
  }

  @SuppressWarnings("unchecked")
  public List<String> getList(String propertyName) {
    List<String> propertyValue =
        getPropertyValue(
            propertyName,
            List.class,
            (properties, lastPart) -> properties.getScalarList(lastPart, String.class));
    return propertyValue == null ? Collections.emptyList() : propertyValue;
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> getMap(String propertyName) {
    DeclarativeConfigProperties propertyValue =
        getPropertyValue(
            propertyName,
            DeclarativeConfigProperties.class,
            DeclarativeConfigProperties::getStructured);
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
      String property,
      Class<T> clazz,
      BiFunction<DeclarativeConfigProperties, String, T> extractor) {
    T override = clazz.cast(overrideValues.get(property));
    if (override != null) {
      return override;
    }

    String[] segments = getSegments(translateProperty(property));
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

  static String[] getSegments(String property) {
    if (property.startsWith(OTEL_INSTRUMENTATION_PREFIX)) {
      property = property.substring(OTEL_INSTRUMENTATION_PREFIX.length());
    }
    // Split the remainder of the property on "."
    return property.replace('-', '_').split("\\.");
  }

  private String translateProperty(String property) {
    for (Map.Entry<String, String> entry : mappings.entrySet()) {
      if (property.startsWith(entry.getKey())) {
        return entry.getValue() + property.substring(entry.getKey().length());
      }
    }
    return property;
  }
}
