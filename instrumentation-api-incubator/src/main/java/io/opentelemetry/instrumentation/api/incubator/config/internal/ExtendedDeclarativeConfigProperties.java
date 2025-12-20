/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExtendedDeclarativeConfigProperties implements DeclarativeConfigProperties {

  private final DeclarativeConfigProperties delegate;

  ExtendedDeclarativeConfigProperties(DeclarativeConfigProperties delegate) {
    this.delegate = delegate;
  }

  public ExtendedDeclarativeConfigProperties get(String name) {
    return new ExtendedDeclarativeConfigProperties(delegate.getStructured(name, empty()));
  }

  @Nullable
  @Override
  public String getString(String name) {
    return delegate.getString(name);
  }

  @Override
  public String getString(String name, String defaultValue) {
    String value = delegate.getString(name);
    return value != null ? value : defaultValue;
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return delegate.getBoolean(name);
  }

  @Override
  public boolean getBoolean(String name, boolean defaultValue) {
    Boolean value = delegate.getBoolean(name);
    return value != null ? value : defaultValue;
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return delegate.getInt(name);
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return delegate.getLong(name);
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return delegate.getDouble(name);
  }

  @Nullable
  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    return delegate.getScalarList(name, scalarType);
  }

  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType, List<T> defaultValue) {
    List<T> value = delegate.getScalarList(name, scalarType);
    return value != null ? value : defaultValue;
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    return delegate.getStructured(name);
  }

  @Nullable
  @Override
  public List<DeclarativeConfigProperties> getStructuredList(String name) {
    return delegate.getStructuredList(name);
  }

  @Override
  public Set<String> getPropertyKeys() {
    return delegate.getPropertyKeys();
  }

  @Override
  public ComponentLoader getComponentLoader() {
    return delegate.getComponentLoader();
  }
}
