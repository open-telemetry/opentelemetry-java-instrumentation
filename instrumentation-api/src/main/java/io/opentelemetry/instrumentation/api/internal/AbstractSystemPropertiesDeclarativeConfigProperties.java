/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public abstract class AbstractSystemPropertiesDeclarativeConfigProperties
    implements DeclarativeConfigProperties {
  protected final String node;
  @Nullable protected final AbstractSystemPropertiesDeclarativeConfigProperties parent;

  public AbstractSystemPropertiesDeclarativeConfigProperties(
      String node, @Nullable AbstractSystemPropertiesDeclarativeConfigProperties parent) {
    this.node = node;
    this.parent = parent;
  }

  private static List<String> filterBlanksAndNulls(String[] values) {
    return Arrays.stream(values)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
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

  @SuppressWarnings("unchecked") // only String scalar type is supported
  @Nullable
  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    if (scalarType != String.class) {
      throw new UnsupportedOperationException("Only String scalar type is supported");
    }

    String value = getString(name);
    return value == null
        ? null
        : (List<T>)
            AbstractSystemPropertiesDeclarativeConfigProperties.filterBlanksAndNulls(
                value.split(","));
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    return newChild(name);
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

  protected abstract AbstractSystemPropertiesDeclarativeConfigProperties newChild(String node);

  protected String getSystemProperty(String name) {
    List<String> nodes = new ArrayList<>();
    addSystemPropertyPathNodes(nodes);
    nodes.add(name);
    return ConfigPropertiesUtil.toSystemProperty(nodes);
  }

  private void addSystemPropertyPathNodes(List<String> parts) {
    if (parent != null) {
      parent.addSystemPropertyPathNodes(parts);
    }
    parts.add(node);
  }
}
