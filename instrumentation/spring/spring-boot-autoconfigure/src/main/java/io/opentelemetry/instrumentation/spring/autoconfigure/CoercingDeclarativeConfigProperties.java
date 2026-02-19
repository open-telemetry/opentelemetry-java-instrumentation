/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A decorator around {@link DeclarativeConfigProperties} that coerces types, because Spring doesn't
 * preserve the original types from YAML (values may arrive as strings).
 *
 * <p>This replaces the previous approach of copying the entire {@code
 * YamlDeclarativeConfigProperties} class.
 */
final class CoercingDeclarativeConfigProperties implements DeclarativeConfigProperties {

  private final DeclarativeConfigProperties delegate;

  private CoercingDeclarativeConfigProperties(DeclarativeConfigProperties delegate) {
    this.delegate = delegate;
  }

  static DeclarativeConfigProperties wrap(@Nullable DeclarativeConfigProperties delegate) {
    if (delegate == null) {
      return DeclarativeConfigProperties.empty();
    }
    if (delegate instanceof CoercingDeclarativeConfigProperties) {
      return delegate;
    }
    return new CoercingDeclarativeConfigProperties(delegate);
  }

  @Nullable
  @Override
  public String getString(String name) {
    return delegate.getString(name);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    // Try getString first to avoid triggering a warning in YamlDeclarativeConfigProperties
    // when the value is stored as a String instead of Boolean.
    String str = delegate.getString(name);
    if (str != null) {
      return Boolean.parseBoolean(str);
    }
    return delegate.getBoolean(name);
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    String str = delegate.getString(name);
    if (str != null) {
      return Integer.parseInt(str);
    }
    return delegate.getInt(name);
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    String str = delegate.getString(name);
    if (str != null) {
      return Long.parseLong(str);
    }
    return delegate.getLong(name);
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    String str = delegate.getString(name);
    if (str != null) {
      return Double.parseDouble(str);
    }
    return delegate.getDouble(name);
  }

  @Nullable
  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    return delegate.getScalarList(name, scalarType);
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    DeclarativeConfigProperties value = delegate.getStructured(name);
    if (value != null) {
      return wrap(value);
    }
    return null;
  }

  @Nullable
  @Override
  public List<DeclarativeConfigProperties> getStructuredList(String name) {
    List<DeclarativeConfigProperties> value = delegate.getStructuredList(name);
    if (value != null) {
      return value.stream().map(CoercingDeclarativeConfigProperties::wrap).collect(toList());
    }
    return null;
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
    return "CoercingDeclarativeConfigProperties{delegate=" + delegate + '}';
  }
}
