/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

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
 * <p>This allows instrumentations to use the declarative config API even when the user configured
 * with system properties (not YAML). It uses a dynamic bridge approach that tracks the navigation
 * path and only resolves to system properties at the leaf node when a value is actually requested.
 */
public final class ConfigPropertiesBackedDeclarativeConfigProperties
    implements DeclarativeConfigProperties {

  private final ConfigProperties configProperties;
  private final List<String> path;

  /**
   * Structured list mapping definition. Maps a declarative config path to a system property key and
   * defines how to convert each map entry to DeclarativeConfigProperties field names.
   */
  private static final class StructuredListMapping {
    final String systemPropertyKey;
    final String keyFieldName;
    final String valueFieldName;

    StructuredListMapping(String systemPropertyKey, String keyFieldName, String valueFieldName) {
      this.systemPropertyKey = systemPropertyKey;
      this.keyFieldName = keyFieldName;
      this.valueFieldName = valueFieldName;
    }
  }

  // Mapping from declarative config paths to system property keys
  // Path format: "segment1.segment2.propertyName" (excludes "java" segment)
  private static final Map<String, String> PROPERTY_MAPPINGS;
  private static final Map<String, String> LIST_MAPPINGS;
  private static final Map<String, StructuredListMapping> STRUCTURED_LIST_MAPPINGS;

  static {
    PROPERTY_MAPPINGS = new HashMap<>();

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

    STRUCTURED_LIST_MAPPINGS = new HashMap<>();
    STRUCTURED_LIST_MAPPINGS.put(
        "general.peer.service_mapping",
        new StructuredListMapping(
            "otel.instrumentation.common.peer-service-mapping", "peer", "service"));
  }

  /**
   * Creates a root DeclarativeConfigProperties instance backed by ConfigProperties.
   *
   * @param configProperties the ConfigProperties to read from
   * @return a new DeclarativeConfigProperties instance
   */
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

  private String currentPath() {
    return String.join(".", path);
  }

  private String pathWithName(String name) {
    if (path.isEmpty()) {
      return name;
    }
    return currentPath() + "." + name;
  }

  /**
   * Converts a declarative config path to a system property key.
   *
   * <p>The declarative config path (e.g., "common.enabled") is converted to a system property key
   * (e.g., "otel.instrumentation.common.enabled") by:
   *
   * <ol>
   *   <li>Checking explicit mappings first
   *   <li>Falling back to standard conversion: prefix + kebab-case path
   * </ol>
   *
   * <p>Special handling for "javaagent" prefix: paths starting with "javaagent." are mapped to
   * "otel.javaagent." instead of "otel.instrumentation.javaagent.".
   */
  private static String toPropertyKey(String fullPath) {
    // Check explicit mappings first
    String mapped = PROPERTY_MAPPINGS.get(fullPath);
    if (mapped != null) {
      return mapped;
    }

    String translatedPath = translatePath(fullPath);

    // Handle javaagent prefix specially: otel.javaagent.* instead of otel.instrumentation.javaagent.*
    if (translatedPath.startsWith("javaagent.")) {
      return "otel." + translatedPath;
    }

    // Standard conversion: otel.instrumentation. + kebab-case path
    return "otel.instrumentation." + translatedPath;
  }

  /**
   * Translates a declarative config path to a system property path segment.
   *
   * <p>Handles:
   *
   * <ul>
   *   <li>Removing "java" segment (not present in system properties)
   *   <li>Converting snake_case to kebab-case
   *   <li>Converting "/development" suffix to "experimental-" prefix
   * </ul>
   */
  private static String translatePath(String path) {
    StringBuilder result = new StringBuilder();
    String[] segments = path.split("\\.");
    boolean first = true;
    for (String segment : segments) {
      // Skip "java" segment - it doesn't exist in system properties
      if ("java".equals(segment)) {
        continue;
      }
      if (!first) {
        result.append(".");
      }
      first = false;
      result.append(translateName(segment));
    }
    return result.toString();
  }

  /**
   * Translates a single declarative config name segment to system property format.
   *
   * <p>Handles the "/development" suffix convention used for experimental properties in declarative
   * config, translating it to the "experimental." prefix used in flat properties.
   */
  private static String translateName(String name) {
    // Handle /development suffix → experimental. prefix
    // e.g., "controller_telemetry/development" → "experimental.controller-telemetry"
    if (name.endsWith("/development")) {
      return "experimental."
          + name.substring(0, name.length() - "/development".length()).replace('_', '-');
    }
    // Convert snake_case to kebab-case (the convention for flat properties)
    return name.replace('_', '-');
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

  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    // Extend the path and return a new instance
    List<String> newPath = new ArrayList<>(path);
    newPath.add(name);
    return new ConfigPropertiesBackedDeclarativeConfigProperties(configProperties, newPath);
  }

  @Nullable
  @Override
  // Safe cast: we verified scalarType == String.class before casting
  @SuppressWarnings("unchecked")
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

    // Fall back to standard property key
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

    // Check explicit structured list mappings
    StructuredListMapping mapping = STRUCTURED_LIST_MAPPINGS.get(fullPath);
    if (mapping != null) {
      Map<String, String> map = configProperties.getMap(mapping.systemPropertyKey);
      if (map.isEmpty()) {
        return null;
      }
      List<DeclarativeConfigProperties> result = new ArrayList<>();
      for (Map.Entry<String, String> entry : map.entrySet()) {
        Map<String, String> fields = new HashMap<>();
        fields.put(mapping.keyFieldName, entry.getKey());
        fields.put(mapping.valueFieldName, entry.getValue());
        result.add(new MapBackedDeclarativeConfig(fields));
      }
      return result;
    }

    // Not supported for other paths when backed by flat properties
    return null;
  }

  @Override
  public Set<String> getPropertyKeys() {
    // Would need to scan all ConfigProperties keys with this prefix
    // and extract immediate child names - not easily supported
    return Collections.emptySet();
  }

  @Override
  public ComponentLoader getComponentLoader() {
    // ComponentLoader is used during SDK setup to load SPI components.
    // When using system properties (not YAML), component loading happens through
    // ServiceLoader instead of declarative config, so this method is not expected
    // to be called from instrumentation code.
    throw new UnsupportedOperationException(
        "ComponentLoader is not available when using system property configuration. "
            + "This method is only used during SDK setup with YAML configuration.");
  }

  /**
   * Generic map-backed DeclarativeConfigProperties for structured list entries.
   *
   * <p>This allows any key-value structure to be represented without hardcoding field names.
   */
  private static final class MapBackedDeclarativeConfig implements DeclarativeConfigProperties {
    private final Map<String, String> fields;

    MapBackedDeclarativeConfig(Map<String, String> fields) {
      this.fields = fields;
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
    public DeclarativeConfigProperties getStructured(String name) {
      return DeclarativeConfigProperties.empty();
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
      return fields.keySet();
    }

    @Override
    public ComponentLoader getComponentLoader() {
      throw new UnsupportedOperationException();
    }
  }
}
