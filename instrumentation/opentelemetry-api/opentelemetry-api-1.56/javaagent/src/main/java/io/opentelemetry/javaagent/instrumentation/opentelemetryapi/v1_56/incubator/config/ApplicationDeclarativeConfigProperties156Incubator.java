/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator.config;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public final class ApplicationDeclarativeConfigProperties156Incubator
    implements application.io.opentelemetry.api.incubator.config.DeclarativeConfigProperties {
  private final DeclarativeConfigProperties instrumentationConfig;

  public ApplicationDeclarativeConfigProperties156Incubator(
      DeclarativeConfigProperties instrumentationConfig) {
    this.instrumentationConfig = instrumentationConfig;
  }

  @Nullable
  @Override
  public String getString(String name) {
    return instrumentationConfig.getString(name);
  }

  @Override
  public String getString(String name, String defaultValue) {
    return instrumentationConfig.getString(name, defaultValue);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return instrumentationConfig.getBoolean(name);
  }

  @Override
  public boolean getBoolean(String name, boolean defaultValue) {
    return instrumentationConfig.getBoolean(name, defaultValue);
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return instrumentationConfig.getInt(name);
  }

  @Override
  public int getInt(String name, int defaultValue) {
    return instrumentationConfig.getInt(name, defaultValue);
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return instrumentationConfig.getLong(name);
  }

  @Override
  public long getLong(String name, long defaultValue) {
    return instrumentationConfig.getLong(name, defaultValue);
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return instrumentationConfig.getDouble(name);
  }

  @Override
  public double getDouble(String name, double defaultValue) {
    return instrumentationConfig.getDouble(name, defaultValue);
  }

  @Nullable
  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    return instrumentationConfig.getScalarList(name, scalarType);
  }

  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType, List<T> defaultValue) {
    return instrumentationConfig.getScalarList(name, scalarType, defaultValue);
  }

  @Nullable
  @Override
  public application.io.opentelemetry.api.incubator.config.DeclarativeConfigProperties
      getStructured(String name) {
    DeclarativeConfigProperties config = instrumentationConfig.getStructured(name);
    return config == null ? null : new ApplicationDeclarativeConfigProperties156Incubator(config);
  }

  @Nullable
  @Override
  public List<application.io.opentelemetry.api.incubator.config.DeclarativeConfigProperties>
      getStructuredList(String name) {
    List<DeclarativeConfigProperties> structuredList =
        instrumentationConfig.getStructuredList(name);
    if (structuredList == null) {
      return null;
    }

    return structuredList.stream()
        .map(e -> new ApplicationDeclarativeConfigProperties156Incubator(e))
        .collect(Collectors.toList());
  }

  @Override
  public Set<String> getPropertyKeys() {
    return instrumentationConfig.getPropertyKeys();
  }

  @Override
  public application.io.opentelemetry.common.ComponentLoader getComponentLoader() {
    throw new application.io.opentelemetry.api.incubator.config.DeclarativeConfigException(
        "getComponentLoader is not supported in application code. "
            + "It is only used to set up the OpenTelemetry SDK in the agent.");
  }
}
