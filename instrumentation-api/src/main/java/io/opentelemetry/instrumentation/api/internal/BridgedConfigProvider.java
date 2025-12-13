/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class BridgedConfigProvider implements ConfigProvider {

  private final Function<String, String> propertySource;

  public BridgedConfigProvider(Function<String, String> propertySource) {
    this.propertySource = propertySource;
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return createEmptyDeclarativeConfigProperties(
        java -> {
          if (java.equals("java")) {
            return createEmptyDeclarativeConfigProperties(
                name -> new BridgedDeclarativeConfigProperties(name, null, propertySource));
          }
          throw new UnsupportedOperationException();
        });
  }

  private static DeclarativeConfigProperties createEmptyDeclarativeConfigProperties(
      Function<String, DeclarativeConfigProperties> getStructuredFunc) {
    return EmptyDeclarativeConfigPropertiesFactory.create(getStructuredFunc);
  }

  private static final class EmptyDeclarativeConfigPropertiesFactory {
    private EmptyDeclarativeConfigPropertiesFactory() {}

    static DeclarativeConfigProperties create(
        Function<String, DeclarativeConfigProperties> getStructuredFunc) {
      return new DeclarativeConfigPropertiesImpl(getStructuredFunc);
    }
  }

  private static final class DeclarativeConfigPropertiesImpl
      implements DeclarativeConfigProperties {
    private final Function<String, DeclarativeConfigProperties> getStructuredFunc;

    DeclarativeConfigPropertiesImpl(
        Function<String, DeclarativeConfigProperties> getStructuredFunc) {
      this.getStructuredFunc = getStructuredFunc;
    }

    @Nullable
    @Override
    public String getString(String name) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Boolean getBoolean(String name) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Integer getInt(String name) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Long getLong(String name) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Double getDouble(String name) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public <T> List<T> getScalarList(String name, Class<T> scalarType) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public DeclarativeConfigProperties getStructured(String name) {
      return getStructuredFunc.apply(name);
    }

    @Nullable
    @Override
    public List<DeclarativeConfigProperties> getStructuredList(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getPropertyKeys() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ComponentLoader getComponentLoader() {
      throw new UnsupportedOperationException();
    }
  }
}
