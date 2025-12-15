/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Collections.emptySet;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Implementation of {@link DeclarativeConfigProperties} backed by system properties and
 * environmental variables.
 *
 * <p>It tracks the navigation path and only resolves to system properties at the leaf node when a
 * value is actually requested.
 */
final class SystemPropertiesBackedDeclarativeConfigProperties
    implements DeclarativeConfigProperties {

  private final List<String> path;

  public static DeclarativeConfigProperties createInstrumentationConfig() {
    return new SystemPropertiesBackedDeclarativeConfigProperties(Collections.emptyList());
  }

  private SystemPropertiesBackedDeclarativeConfigProperties(List<String> path) {
    this.path = path;
  }

  @Nullable
  @Override
  public String getString(String name) {
    String fullPath = pathWithName(name);
    return ConfigPropertiesUtil.getString(toPropertyKey(fullPath));
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    String value = getString(name);
    return value == null ? null : Boolean.parseBoolean(value);
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    String strValue = getString(name);
    if (strValue == null) {
      return null;
    }
    try {
      return Integer.parseInt(strValue);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    String strValue = getString(name);
    if (strValue == null) {
      return null;
    }
    try {
      return Long.getLong(strValue);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    String strValue = getString(name);
    if (strValue == null) {
      return null;
    }
    try {
      return Double.parseDouble(strValue);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  /**
   * Important: this method should return null if there is no structured child with the given name,
   * but unfortunately that is not implementable on top of ConfigProperties.
   *
   * <p>This will be misleading if anyone is comparing the return value to null.
   */
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    List<String> newPath = new ArrayList<>(path);
    newPath.add(name);
    return new SystemPropertiesBackedDeclarativeConfigProperties(newPath);
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked") // Safe because T is known to be String via scalarType check
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    if (scalarType != String.class) {
      return null;
    }
    String value = getString(name);
    return value == null ? null : (List<T>) filterBlanksAndNulls(value.split(","));
  }

  @Nullable
  @Override
  public List<DeclarativeConfigProperties> getStructuredList(String name) {
    return null;
  }

  @Override
  public Set<String> getPropertyKeys() {
    // this is not supported when using system properties based configuration
    return emptySet();
  }

  @Override
  public ComponentLoader getComponentLoader() {
    throw new UnsupportedOperationException();
  }

  private String pathWithName(String name) {
    if (path.isEmpty()) {
      return name;
    }
    return String.join(".", path) + "." + name;
  }

  private static String toPropertyKey(String fullPath) {
    String translatedPath = translatePath(fullPath);
    // Standard mapping
    return "otel.instrumentation." + translatedPath;
  }

  private static String translatePath(String path) {
    StringBuilder result = new StringBuilder();
    for (String segment : path.split("\\.")) {
      // Skip "java" segment - it doesn't exist in system properties
      if ("java".equals(segment)) {
        continue;
      }
      if (result.length() > 0) {
        result.append(".");
      }
      result.append(translateName(segment));
    }
    return result.toString();
  }

  private static String translateName(String name) {
    if (name.endsWith("/development")) {
      return "experimental."
          + name.substring(0, name.length() - "/development".length()).replace('_', '-');
    }
    return name.replace('_', '-');
  }

  private static List<String> filterBlanksAndNulls(String[] values) {
    return Arrays.stream(values)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
