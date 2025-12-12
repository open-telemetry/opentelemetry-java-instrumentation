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
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public abstract class AbstractSystemPropertiesConfigProvider implements ConfigProvider {
  @Nullable
  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return new EmptyDeclarativeConfigProperties() {
      @Nullable
      @Override
      public DeclarativeConfigProperties getStructured(String name) {
        if (name.equals("java")) {
          return new EmptyDeclarativeConfigProperties() {
            @Override
            public DeclarativeConfigProperties getStructured(String name) {
              return getProperties(name);
            }
          };
        }
        return super.getStructured(name);
      }
    };
  }

  protected abstract AbstractSystemPropertiesDeclarativeConfigProperties getProperties(String name);

  private abstract static class EmptyDeclarativeConfigProperties
      implements DeclarativeConfigProperties {
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
      throw new UnsupportedOperationException();
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
