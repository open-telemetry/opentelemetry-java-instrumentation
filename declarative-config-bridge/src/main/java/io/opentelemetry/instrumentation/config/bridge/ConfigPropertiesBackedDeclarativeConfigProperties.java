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

  private static final Map<String, String> SPECIAL_MAPPINGS;

  static {
    SPECIAL_MAPPINGS = new HashMap<>();
    SPECIAL_MAPPINGS.put(
        "general.http.client.request_captured_headers",
        "otel.instrumentation.http.client.capture-request-headers");
    SPECIAL_MAPPINGS.put(
        "general.http.client.response_captured_headers",
        "otel.instrumentation.http.client.capture-response-headers");
    SPECIAL_MAPPINGS.put(
        "general.http.server.request_captured_headers",
        "otel.instrumentation.http.server.capture-request-headers");
    SPECIAL_MAPPINGS.put(
        "general.http.server.response_captured_headers",
        "otel.instrumentation.http.server.capture-response-headers");
    SPECIAL_MAPPINGS.put(
        "java.common.messaging.receive_telemetry/development.enabled",
        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled");
    SPECIAL_MAPPINGS.put(
        "java.common.messaging.capture_headers/development",
        "otel.instrumentation.messaging.experimental.capture-headers");
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
    return configProperties.getString(resolvePropertyKey(name));
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return configProperties.getBoolean(resolvePropertyKey(name));
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return configProperties.getInt(resolvePropertyKey(name));
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return configProperties.getLong(resolvePropertyKey(name));
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return configProperties.getDouble(resolvePropertyKey(name));
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
    List<String> list = configProperties.getList(resolvePropertyKey(name));
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

  private String resolvePropertyKey(String name) {
    String fullPath = pathWithName(name);

    // Check explicit property mappings first
    String mappedKey = SPECIAL_MAPPINGS.get(fullPath);
    if (mappedKey != null) {
      return mappedKey;
    }

    if (!fullPath.startsWith("java.")) {
      return "";
    }

    // Remove "java." prefix and translate the remaining path
    String[] segments = fullPath.substring(5).split("\\.");
    StringBuilder translatedPath = new StringBuilder();

    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        translatedPath.append(".");
      }
      translatedPath.append(translateName(segments[i]));
    }

    String translated = translatedPath.toString();

    // Handle agent prefix: java.agent.* → otel.javaagent.*
    if (translated.startsWith("agent.")) {
      return "otel.java" + translated;
    }

    // Handle jmx prefix: java.jmx.* → otel.jmx.*
    if (translated.startsWith("jmx.")) {
      return "otel." + translated;
    }

    // Standard mapping
    return "otel.instrumentation." + translated;
  }

  private String pathWithName(String name) {
    if (path.isEmpty()) {
      return name;
    }
    return String.join(".", path) + "." + name;
  }

  private static String translateName(String name) {
    if (name.endsWith("/development")) {
      name = name.substring(0, name.length() - "/development".length());
      if (!name.contains("experimental")) {
        name = "experimental." + name;
      }
    }
    return name.replace('_', '-');
  }
}
