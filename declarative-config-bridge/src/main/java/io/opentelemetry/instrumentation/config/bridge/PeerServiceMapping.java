/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.util.Collections.emptySet;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

final class PeerServiceMapping implements DeclarativeConfigProperties {

  private final Map<String, String> fields;
  private final ComponentLoader componentLoader;

  @Nullable
  static List<DeclarativeConfigProperties> getList(ConfigProperties configProperties) {
    Map<String, String> map =
        configProperties.getMap("otel.instrumentation.common.peer-service-mapping");
    if (map.isEmpty()) {
      return null;
    }
    List<DeclarativeConfigProperties> result = new ArrayList<>();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      Map<String, String> fields = new HashMap<>();
      fields.put("peer", entry.getKey());
      fields.put("service", entry.getValue());
      result.add(new PeerServiceMapping(fields, configProperties.getComponentLoader()));
    }
    return result;
  }

  private PeerServiceMapping(Map<String, String> fields, ComponentLoader componentLoader) {
    this.fields = fields;
    this.componentLoader = componentLoader;
  }

  @Nullable
  @Override
  public String getString(String name) {
    return fields.get(name);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return null;
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return null;
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return null;
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return null;
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    return null;
  }

  @Nullable
  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    return null;
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
    return componentLoader;
  }
}
