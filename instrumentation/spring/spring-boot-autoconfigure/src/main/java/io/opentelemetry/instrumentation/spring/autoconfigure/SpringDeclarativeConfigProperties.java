/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.YamlDeclarativeConfigProperties;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Wraps {@link YamlDeclarativeConfigProperties} so typed getters convert Spring-resolved values to
 * the requested type rather than requiring the stored scalar to already have that type. Values
 * resolved from properties, placeholders, and system-property overrides arrive as Strings.
 *
 * <p>Getters check what the delegate holds with its {@code is<Type>} predicates before reading, so
 * the delegate does not log a type mismatch for a value this class converts. List elements have no
 * such predicate, so {@link #create} stores every scalar list element as a String and {@link
 * #getScalarList} converts from there.
 */
final class SpringDeclarativeConfigProperties implements DeclarativeConfigProperties {

  private static final Set<Class<?>> CONVERTIBLE_LIST_TYPES =
      new HashSet<>(asList(Boolean.class, Long.class, Double.class));

  private final DeclarativeConfigProperties delegate;

  static SpringDeclarativeConfigProperties create(
      Map<String, Object> properties, ComponentLoader componentLoader) {
    return new SpringDeclarativeConfigProperties(
        YamlDeclarativeConfigProperties.create(stringifyScalarLists(properties), componentLoader));
  }

  private SpringDeclarativeConfigProperties(DeclarativeConfigProperties delegate) {
    this.delegate = delegate;
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
    Long value = getLong(name);
    return value == null ? null : value.intValue();
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
    if (!CONVERTIBLE_LIST_TYPES.contains(scalarType)) {
      // String, or an unsupported type the delegate rejects
      return delegate.getScalarList(name, scalarType);
    }
    List<String> strings = delegate.getScalarList(name, String.class);
    if (strings == null) {
      return null;
    }
    if (strings.isEmpty()) {
      return emptyList();
    }
    List<T> result =
        strings.stream()
            .map(value -> parse(value, scalarType))
            .filter(Objects::nonNull)
            .map(scalarType::cast)
            .collect(toList());
    return result.isEmpty() ? null : result;
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    DeclarativeConfigProperties value = delegate.getStructured(name);
    return value == null ? null : new SpringDeclarativeConfigProperties(value);
  }

  @Nullable
  @Override
  public List<DeclarativeConfigProperties> getStructuredList(String name) {
    List<DeclarativeConfigProperties> value = delegate.getStructuredList(name);
    if (value == null) {
      return null;
    }
    return unmodifiableList(
        value.stream().map(SpringDeclarativeConfigProperties::new).collect(toList()));
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

  @Nullable
  private static Object parse(String value, Class<?> scalarType) {
    if (scalarType == Boolean.class) {
      return parseBoolean(value);
    }
    if (scalarType == Long.class) {
      return parseLong(value);
    }
    return parseDouble(value);
  }

  @Nullable
  private static Boolean parseBoolean(@Nullable String value) {
    return value == null ? null : Boolean.parseBoolean(value);
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

  private static Map<String, Object> stringifyScalarLists(Map<String, Object> properties) {
    Map<String, Object> result = new LinkedHashMap<>();
    properties.forEach((key, value) -> result.put(key, stringifyScalarLists(value)));
    return result;
  }

  @SuppressWarnings("unchecked") // nested maps come from the same parsed config, keyed by String
  @Nullable
  private static Object stringifyScalarLists(@Nullable Object value) {
    if (value instanceof Map) {
      return stringifyScalarLists((Map<String, Object>) value);
    }
    if (!(value instanceof List)) {
      return value;
    }
    List<?> list = (List<?>) value;
    if (list.stream().allMatch(SpringDeclarativeConfigProperties::isScalar)) {
      return list.stream().map(String::valueOf).collect(toList());
    }
    return list.stream()
        .map(SpringDeclarativeConfigProperties::stringifyScalarLists)
        .collect(toList());
  }

  private static boolean isScalar(Object value) {
    return value instanceof String || value instanceof Number || value instanceof Boolean;
  }
}
