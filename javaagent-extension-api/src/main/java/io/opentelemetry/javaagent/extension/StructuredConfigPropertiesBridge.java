/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension;

import static io.opentelemetry.sdk.autoconfigure.spi.internal.StructuredConfigProperties.empty;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.StructuredConfigProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * A {@link ConfigProperties} which resolves properties based on {@link StructuredConfigProperties}.
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
 *       StructuredConfigProperties}.
 *   <li>We extract the property from the resolved {@link StructuredConfigProperties} using the last
 *       segment as the property key.
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
final class StructuredConfigPropertiesBridge implements ConfigProperties {

  private static final String OTEL_INSTRUMENTATION_PREFIX = "otel.instrumentation.";

  // The node at .instrumentation.java
  private final StructuredConfigProperties instrumentationJavaNode;

  StructuredConfigPropertiesBridge(StructuredConfigProperties rootStructuredConfigProperties) {
    instrumentationJavaNode =
        rootStructuredConfigProperties
            .getStructured("instrumentation", empty())
            .getStructured("java", empty());
  }

  @Nullable
  @Override
  public String getString(String propertyName) {
    return getPropertyValue(propertyName, StructuredConfigProperties::getString);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String propertyName) {
    return getPropertyValue(propertyName, StructuredConfigProperties::getBoolean);
  }

  @Nullable
  @Override
  public Integer getInt(String propertyName) {
    return getPropertyValue(propertyName, StructuredConfigProperties::getInt);
  }

  @Nullable
  @Override
  public Long getLong(String propertyName) {
    return getPropertyValue(propertyName, StructuredConfigProperties::getLong);
  }

  @Nullable
  @Override
  public Double getDouble(String propertyName) {
    return getPropertyValue(propertyName, StructuredConfigProperties::getDouble);
  }

  @Nullable
  @Override
  public Duration getDuration(String propertyName) {
    Long millis = getPropertyValue(propertyName, StructuredConfigProperties::getLong);
    if (millis == null) {
      return null;
    }
    return Duration.ofMillis(millis);
  }

  @Override
  public List<String> getList(String propertyName) {
    List<String> propertyValue =
        getPropertyValue(
            propertyName,
            (properties, lastPart) -> properties.getScalarList(lastPart, String.class));
    return propertyValue == null ? Collections.emptyList() : propertyValue;
  }

  @Override
  public Map<String, String> getMap(String propertyName) {
    StructuredConfigProperties propertyValue =
        getPropertyValue(propertyName, StructuredConfigProperties::getStructured);
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
      String property, BiFunction<StructuredConfigProperties, String, T> extractor) {
    if (!property.startsWith(OTEL_INSTRUMENTATION_PREFIX)) {
      return null;
    }
    String suffix = property.substring(OTEL_INSTRUMENTATION_PREFIX.length());
    // Split the remainder of the property on ".", and walk to the N-1 entry
    String[] segments = suffix.split("\\.");
    if (segments.length == 0) {
      return null;
    }
    StructuredConfigProperties target = instrumentationJavaNode;
    if (segments.length > 1) {
      for (int i = 0; i < segments.length - 1; i++) {
        target = target.getStructured(segments[i], empty());
      }
    }
    String lastPart = segments[segments.length - 1];
    return extractor.apply(target, lastPart);
  }
}
