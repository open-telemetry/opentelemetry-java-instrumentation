/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
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
 */
final class DeclarativeConfigPropertiesBridge implements ConfigProperties {

  private static final String OTEL_INSTRUMENTATION_PREFIX = "otel.instrumentation.";

  private final DeclarativeConfigProperties baseNode;

  // lookup order matters - we choose the first match
  private final Map<String, String> mappings;
  private final Map<String, Object> overrideValues;

  DeclarativeConfigPropertiesBridge(
      DeclarativeConfigProperties baseNode,
      Map<String, String> mappings,
      Map<String, Object> overrideValues) {
    this.baseNode = Objects.requireNonNull(baseNode);
    this.mappings = mappings;
    this.overrideValues = overrideValues;
  }

  @Nullable
  @Override
  public String getString(String propertyName) {
    return getPropertyValue(propertyName, String.class, DeclarativeConfigProperties::getString);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String propertyName) {
    return getPropertyValue(propertyName, Boolean.class, DeclarativeConfigProperties::getBoolean);
  }

  @Nullable
  @Override
  public Integer getInt(String propertyName) {
    return getPropertyValue(propertyName, Integer.class, DeclarativeConfigProperties::getInt);
  }

  @Nullable
  @Override
  public Long getLong(String propertyName) {
    return getPropertyValue(propertyName, Long.class, DeclarativeConfigProperties::getLong);
  }

  @Nullable
  @Override
  public Double getDouble(String propertyName) {
    return getPropertyValue(propertyName, Double.class, DeclarativeConfigProperties::getDouble);
  }

  @Nullable
  @Override
  public Duration getDuration(String propertyName) {
    // If this is a raw integer number then assume it is the number of milliseconds
    Long millis = getLong(propertyName);
    if (millis != null) {
      return Duration.ofMillis(millis);
    }

    // If this is a string than it consists of value and time unit
    String value = getString(propertyName);
    if (value == null) {
      return null;
    }
    String unitString = getUnitString(value);
    String numberString = value.substring(0, value.length() - unitString.length());
    try {
      long rawNumber = Long.parseLong(numberString.trim());
      TimeUnit unit = getDurationUnit(unitString.trim());
      return Duration.ofNanos(TimeUnit.NANOSECONDS.convert(rawNumber, unit));
    } catch (NumberFormatException ex) {
      throw new ConfigurationException(
          "Invalid duration property "
              + propertyName
              + "="
              + value
              + ". Expected number, found: "
              + numberString,
          ex);
    } catch (ConfigurationException ex) {
      throw new ConfigurationException(
          "Invalid duration property " + propertyName + "=" + value + ". " + ex.getMessage());
    }
  }

  /** Returns the TimeUnit associated with a unit string. Defaults to milliseconds. */
  private static TimeUnit getDurationUnit(String unitString) {
    switch (unitString) {
      case "us":
        return TimeUnit.MICROSECONDS;
      case "ns":
        return TimeUnit.NANOSECONDS;
      case "": // Fallthrough expected
      case "ms":
        return TimeUnit.MILLISECONDS;
      case "s":
        return TimeUnit.SECONDS;
      case "m":
        return TimeUnit.MINUTES;
      case "h":
        return TimeUnit.HOURS;
      case "d":
        return TimeUnit.DAYS;
      default:
        throw new ConfigurationException("Invalid duration string, found: " + unitString);
    }
  }

  /**
   * Fragments the 'units' portion of a config value from the 'value' portion.
   *
   * <p>E.g. "1ms" would return the string "ms".
   */
  private static String getUnitString(String rawValue) {
    int lastDigitIndex = rawValue.length() - 1;
    while (lastDigitIndex >= 0) {
      char c = rawValue.charAt(lastDigitIndex);
      if (Character.isDigit(c)) {
        break;
      }
      lastDigitIndex--;
    }
    // Pull everything after the last digit.
    return rawValue.substring(lastDigitIndex + 1);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getList(String propertyName) {
    List<String> propertyValue =
        getPropertyValue(
            propertyName,
            List.class,
            (properties, lastPart) -> properties.getScalarList(lastPart, String.class));
    return propertyValue == null ? Collections.emptyList() : propertyValue;
  }

  @SuppressWarnings("unchecked")
  @Override
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
