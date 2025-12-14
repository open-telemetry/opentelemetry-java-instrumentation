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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Implementation of {@link DeclarativeConfigProperties} backed by {@link ConfigProperties}.
 *
 * <p>It tracks the navigation path and only resolves to system properties at the leaf node when a
 * value is actually requested.
 */
public final class ConfigPropertiesBackedDeclarativeConfigProperties
    implements DeclarativeConfigProperties {

  private static final String GENERAL_PEER_SERVICE_MAPPING = "general.peer.service_mapping";

  private static final Map<String, String> LIST_MAPPINGS;

  static {
    LIST_MAPPINGS = new HashMap<>();
    LIST_MAPPINGS.put(
        "general.http.client.request_captured_headers",
        "otel.instrumentation.http.client.capture-request-headers");
    LIST_MAPPINGS.put(
        "general.http.client.response_captured_headers",
        "otel.instrumentation.http.client.capture-response-headers");
    LIST_MAPPINGS.put(
        "general.http.server.request_captured_headers",
        "otel.instrumentation.http.server.capture-request-headers");
    LIST_MAPPINGS.put(
        "general.http.server.response_captured_headers",
        "otel.instrumentation.http.server.capture-response-headers");
  }

  private final ConfigProperties configProperties;
  private final List<String> path;

  public static DeclarativeConfigProperties createInstrumentationConfig(
      ConfigProperties configProperties) {
    return new ConfigPropertiesBackedDeclarativeConfigProperties(
        configProperties, Collections.emptyList());
  }

  private ConfigPropertiesBackedDeclarativeConfigProperties(
      ConfigProperties configProperties, List<String> path) {
    this.configProperties = configProperties;
    this.path = path;
  }

  @Nullable
  @Override
  public String getString(String name) {
    String fullPath = pathWithName(name);
    return configProperties.getString(toPropertyKey(fullPath));
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    String fullPath = pathWithName(name);
    return configProperties.getBoolean(toPropertyKey(fullPath));
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    String fullPath = pathWithName(name);
    return configProperties.getInt(toPropertyKey(fullPath));
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    String fullPath = pathWithName(name);
    return configProperties.getLong(toPropertyKey(fullPath));
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    String fullPath = pathWithName(name);
    return configProperties.getDouble(toPropertyKey(fullPath));
  }

  /**
   * Important: this method should return null if there is no structured child with the given name,
   * but unfortunately that is not implementable on top of ConfigProperties.
   *
   * <p>This will be misleading if anyone is comparing the return value to null.
   */
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    List<String> newPath = new ArrayList<>(path);
    newPath.add(name);
    return new ConfigPropertiesBackedDeclarativeConfigProperties(configProperties, newPath);
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked") // Safe because T is known to be String via scalarType check
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    if (scalarType != String.class) {
      return null;
    }
    String fullPath = pathWithName(name);

    // Check explicit list mappings first
    String mappedKey = LIST_MAPPINGS.get(fullPath);
    if (mappedKey != null) {
      List<String> list = configProperties.getList(mappedKey);
      if (!list.isEmpty()) {
        return (List<T>) list;
      }
      return null;
    }

    // Standard mapping
    List<String> list = configProperties.getList(toPropertyKey(fullPath));
    if (list.isEmpty()) {
      return null;
    }
    return (List<T>) list;
  }

  @Nullable
  @Override
  public List<DeclarativeConfigProperties> getStructuredList(String name) {
    String fullPath = pathWithName(name);
    if (GENERAL_PEER_SERVICE_MAPPING.equals(fullPath)) {
      return PeerServiceMapping.getList(configProperties);
    }
    return null;
  }

  @Override
  public Set<String> getPropertyKeys() {
    // this is not supported when using system properties based configuration
    return emptySet();
  }

  @Override
  public ComponentLoader getComponentLoader() {
    return configProperties.getComponentLoader();
  }

  private String pathWithName(String name) {
    if (path.isEmpty()) {
      return name;
    }
    return String.join(".", path) + "." + name;
  }

  private static String toPropertyKey(String fullPath) {
    String translatedPath = translatePath(fullPath);

    // Handle agent prefix: java.agent.* → otel.javaagent.*
    if (translatedPath.startsWith("agent.")) {
      return "otel.java" + translatedPath;
    }

    // Handle jmx prefix: java.jmx.* → otel.jmx.*
    if (translatedPath.startsWith("jmx.")) {
      return "otel." + translatedPath;
    }

    // Standard mapping
    return "otel.instrumentation." + translatedPath;
  }

  private static String translatePath(String path) {
    StringBuilder result = new StringBuilder();
    for (String segment : path.split("\\.")) {
      // Skip "java" segment - it doesn't exist in system properties
      if ("java".equals(segment)) {
        continue;
      }
      if (result.length() > 0) {
        result.append(".");
      }
      result.append(translateName(segment));
    }
    return result.toString();
  }

  private static String translateName(String name) {
    if (name.endsWith("/development")) {
      return "experimental."
          + name.substring(0, name.length() - "/development".length()).replace('_', '-');
    }
    return name.replace('_', '-');
  }
}
