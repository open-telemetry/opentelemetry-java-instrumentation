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

/**
 * Bridges the flat config property {@code otel.instrumentation.common.server-service-mapping} to
 * the declarative config structure at {@code java.common.server_service_mapping}.
 *
 * <p>The flat property format is: {@code target1=service_name1,target2=service_name2}, where each
 * target can be a hostname, IP address, or target including a port or path.
 *
 * <p>The declarative config format is:
 *
 * <pre>
 * server_service_mapping:
 *   - target: target1
 *     service_name: service_name1
 *   - target: target2
 *     service_name: service_name2
 * </pre>
 *
 * <p>The flat property cannot express {@code service_namespace} or targets containing commas or
 * equals signs. Use declarative configuration for those cases.
 */
final class ServicePeerMapping implements DeclarativeConfigProperties {

  private final Map<String, String> fields;
  private final ComponentLoader componentLoader;

  @Nullable
  static List<DeclarativeConfigProperties> getList(ConfigProperties configProperties) {
    Map<String, String> map =
        configProperties.getMap("otel.instrumentation.common.server-service-mapping");
    if (map.isEmpty()) {
      return null;
    }
    List<DeclarativeConfigProperties> result = new ArrayList<>();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      Map<String, String> fields = new HashMap<>();
      fields.put("target", entry.getKey());
      fields.put("service_name", entry.getValue());
      // service_namespace is not supported in flat config, will be null
      result.add(new ServicePeerMapping(fields, configProperties.getComponentLoader()));
    }
    return result;
  }

  private ServicePeerMapping(Map<String, String> fields, ComponentLoader componentLoader) {
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

  @Override
  public boolean isString(String name) {
    return getString(name) != null;
  }

  @Override
  public boolean isBoolean(String name) {
    return false;
  }

  @Override
  public boolean isInt(String name) {
    return false;
  }

  @Override
  public boolean isLong(String name) {
    return false;
  }

  @Override
  public boolean isDouble(String name) {
    return false;
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
