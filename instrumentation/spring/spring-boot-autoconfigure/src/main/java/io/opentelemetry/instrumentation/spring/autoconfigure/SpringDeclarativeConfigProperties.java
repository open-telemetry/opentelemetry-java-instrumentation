/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.YamlDeclarativeConfigProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

/**
 * Wraps {@link YamlDeclarativeConfigProperties} so typed getters convert Spring-resolved values to
 * the requested type rather than requiring the stored scalar to already have that type. Values
 * resolved from properties, placeholders, and system-property overrides arrive as Strings.
 *
 * <p>Getters check what the delegate holds with its {@code is<Type>} predicates before reading, so
 * the delegate does not log a type mismatch for a value this class converts. List elements have no
 * such predicate, so {@link #getScalarList} converts from the original values instead.
 */
final class SpringDeclarativeConfigProperties implements DeclarativeConfigProperties {

  private static final Set<Class<?>> SUPPORTED_SCALAR_TYPES =
      new HashSet<>(asList(String.class, Boolean.class, Long.class, Double.class));

  private final DeclarativeConfigProperties delegate;

  /**
   * The values {@link #delegate} was created from, read for scalar list elements and nested values.
   * Callers pass a freshly built map and do not modify it afterwards.
   */
  private final Map<String, Object> properties;

  static SpringDeclarativeConfigProperties create(
      Map<String, Object> properties, ComponentLoader componentLoader) {
    return new SpringDeclarativeConfigProperties(
        YamlDeclarativeConfigProperties.create(properties, componentLoader), properties);
  }

  private SpringDeclarativeConfigProperties(
      DeclarativeConfigProperties delegate, Map<String, Object> properties) {
    this.delegate = delegate;
    this.properties = properties;
  }

  @Nullable
  @Override
  public String getString(String name) {
    if (delegate.isString(name)) {
      return delegate.getString(name);
    }
    if (delegate.isBoolean(name)) {
      return String.valueOf(delegate.getBoolean(name));
    }
    if (delegate.isLong(name)) {
      return String.valueOf(delegate.getLong(name));
    }
    if (delegate.isDouble(name)) {
      return String.valueOf(delegate.getDouble(name));
    }
    return null;
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    if (delegate.isBoolean(name)) {
      return delegate.getBoolean(name);
    }
    return parseBoolean(stringOrNull(name));
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    if (delegate.isInt(name)) {
      // the delegate narrows a Long with Long.intValue(), as the interface documents
      return delegate.getInt(name);
    }
    return parseInt(stringOrNull(name));
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    if (delegate.isLong(name)) {
      return delegate.getLong(name);
    }
    return parseLong(stringOrNull(name));
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    if (delegate.isDouble(name)) {
      return delegate.getDouble(name);
    }
    Long longValue = delegate.isLong(name) ? delegate.getLong(name) : null;
    if (longValue != null) {
      return longValue.doubleValue();
    }
    return parseDouble(stringOrNull(name));
  }

  @Override
  public boolean isString(String name) {
    return getString(name) != null;
  }

  @Override
  public boolean isBoolean(String name) {
    return getBoolean(name) != null;
  }

  @Override
  public boolean isInt(String name) {
    return getInt(name) != null;
  }

  @Override
  public boolean isLong(String name) {
    return getLong(name) != null;
  }

  @Override
  public boolean isDouble(String name) {
    return getDouble(name) != null;
  }

  @Nullable
  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    if (!SUPPORTED_SCALAR_TYPES.contains(scalarType)) {
      // the delegate throws the documented exception
      return delegate.getScalarList(name, scalarType);
    }
    Object value = properties.get(name);
    if (!(value instanceof List)) {
      return null;
    }
    List<?> list = (List<?>) value;
    if (!list.stream().allMatch(SpringDeclarativeConfigProperties::isScalar)) {
      return null;
    }
    if (list.isEmpty()) {
      return emptyList();
    }
    List<T> result =
        list.stream()
            .map(element -> convert(element, scalarType))
            .filter(Objects::nonNull)
            .map(scalarType::cast)
            .collect(toList());
    return result.isEmpty() ? null : result;
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    DeclarativeConfigProperties value = delegate.getStructured(name);
    if (value == null) {
      return null;
    }
    return new SpringDeclarativeConfigProperties(value, structuredProperties(name));
  }

  @Nullable
  @Override
  public List<DeclarativeConfigProperties> getStructuredList(String name) {
    List<DeclarativeConfigProperties> value = delegate.getStructuredList(name);
    if (value == null) {
      return null;
    }
    List<Map<String, Object>> itemProperties = structuredListProperties(name);
    return unmodifiableList(
        IntStream.range(0, value.size())
            .mapToObj(
                i -> new SpringDeclarativeConfigProperties(value.get(i), itemProperties.get(i)))
            .collect(toList()));
  }

  @Override
  public Set<String> getPropertyKeys() {
    return delegate.getPropertyKeys();
  }

  @Override
  public ComponentLoader getComponentLoader() {
    return delegate.getComponentLoader();
  }

  @Override
  public String toString() {
    return "SpringDeclarativeConfigProperties{" + delegate + '}';
  }

  /** Returns the stored String without asking the delegate to read any other type. */
  @Nullable
  private String stringOrNull(String name) {
    return delegate.isString(name) ? delegate.getString(name) : null;
  }

  // the delegate holds a structured value only where the original value is a String-keyed map
  @SuppressWarnings("unchecked")
  private Map<String, Object> structuredProperties(String name) {
    return (Map<String, Object>) requireNonNull(properties.get(name));
  }

  // the delegate holds a structured list only where the original value is a list of such maps
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> structuredListProperties(String name) {
    return (List<Map<String, Object>>) requireNonNull(properties.get(name));
  }

  /** Converts a list element with the same rules as the scalar getters. */
  @Nullable
  private static Object convert(Object element, Class<?> scalarType) {
    if (scalarType == String.class) {
      return String.valueOf(element);
    }
    if (scalarType == Boolean.class) {
      if (element instanceof Boolean) {
        return element;
      }
      return element instanceof String ? parseBoolean((String) element) : null;
    }
    if (scalarType == Long.class) {
      if (element instanceof Integer || element instanceof Long) {
        return ((Number) element).longValue();
      }
      return element instanceof String ? parseLong((String) element) : null;
    }
    if (scalarType == Double.class) {
      if (element instanceof Number) {
        return ((Number) element).doubleValue();
      }
      return element instanceof String ? parseDouble((String) element) : null;
    }
    return null;
  }

  @Nullable
  private static Boolean parseBoolean(@Nullable String value) {
    return value == null ? null : Boolean.parseBoolean(value);
  }

  @Nullable
  private static Integer parseInt(@Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  private static Long parseLong(@Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  private static Double parseDouble(@Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static boolean isScalar(Object value) {
    return value instanceof String || value instanceof Number || value instanceof Boolean;
  }
}
