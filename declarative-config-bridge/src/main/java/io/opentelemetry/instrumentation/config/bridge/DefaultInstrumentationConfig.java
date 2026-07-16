/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines instrumentation defaults that work with both traditional property-based configuration and
 * declarative configuration.
 *
 * <p>Navigation mirrors {@link io.opentelemetry.api.incubator.config.DeclarativeConfigProperties}:
 * read-side uses {@code config.get(name).getString(key)}; write-side uses {@code
 * defaults.get(name).setDefault(key, value)}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * DefaultInstrumentationConfig defaults = new DefaultInstrumentationConfig();
 * defaults.get("micrometer").setDefault("base_time_unit", "s");
 * defaults.get("log4j_appender").setDefault("experimental_log_attributes/development", true);
 * defaults.addMapping("acme", "acme.full_name");
 * defaults.get("acme").get("full_name").setDefault("preserved", true);
 *
 * autoConfiguration.addPropertiesSupplier(defaults::toConfigProperties);
 * }</pre>
 *
 * <p>For declarative-config model integration, call {@link
 * #applyToModel(OpenTelemetryConfigurationModel)}.
 */
public final class DefaultInstrumentationConfig {

  private final Map<String, Object> defaults;
  private final List<String> path;
  private final Map<String, String> propertyMappings;

  public DefaultInstrumentationConfig() {
    this(new HashMap<>(), emptyList(), new HashMap<>());
  }

  private DefaultInstrumentationConfig(
      Map<String, Object> defaults, List<String> path, Map<String, String> propertyMappings) {
    this.defaults = defaults;
    this.path = path;
    this.propertyMappings = propertyMappings;
  }

  /**
   * Returns the defaults node for the given child, mirroring {@code
   * DeclarativeConfigProperties.get(name)} on the read side.
   */
  public DefaultInstrumentationConfig get(String name) {
    List<String> newPath = new ArrayList<>(path);
    newPath.add(name);
    return new DefaultInstrumentationConfig(defaults, newPath, propertyMappings);
  }

  /**
   * Adds a property prefix mapping, mirroring {@link DeclarativeConfigPropertiesBridgeBuilder
   * #addMapping(String, String)} in the opposite direction.
   *
   * <p>For example, mapping {@code acme} to {@code acme.full_name} makes {@code
   * defaults.get("acme").get("full_name").setDefault("preserved", "true")} produce {@code
   * acme.preserved=true}.
   */
  @CanIgnoreReturnValue
  public DefaultInstrumentationConfig addMapping(String propertyPrefix, String declarativePath) {
    propertyMappings.put(propertyPrefix, declarativePath);
    return this;
  }

  /**
   * Sets a default value for a property on the current node. Keys use the declarative config shape
   * (e.g. {@code base_time_unit}); when producing config property keys, underscores are translated
   * to hyphens and keys ending in {@code /development} follow the same {@code experimental.}
   * translation as {@link ConfigPropertiesBackedDeclarativeConfigProperties}.
   *
   * @return {@code this} for chaining
   */
  @CanIgnoreReturnValue
  public DefaultInstrumentationConfig setDefault(String key, String value) {
    return setDefaultValue(key, value);
  }

  @CanIgnoreReturnValue
  public DefaultInstrumentationConfig setDefault(String key, boolean value) {
    return setDefaultValue(key, value);
  }

  @CanIgnoreReturnValue
  public DefaultInstrumentationConfig setDefault(String key, int value) {
    return setDefaultValue(key, value);
  }

  @CanIgnoreReturnValue
  public DefaultInstrumentationConfig setDefault(String key, long value) {
    return setDefaultValue(key, value);
  }

  @CanIgnoreReturnValue
  public DefaultInstrumentationConfig setDefault(String key, double value) {
    return setDefaultValue(key, value);
  }

  /**
   * Translates defaults to config properties for auto-configuration.
   *
   * <p>Defaults use {@code otel.instrumentation.*} keys unless a custom mapping overrides the
   * property prefix for a declarative path subtree.
   */
  public Map<String, String> toConfigProperties() {
    HashMap<String, String> map = new HashMap<>();
    defaults.forEach(
        (declarativePath, value) ->
            map.put(toConfigProperty(declarativePath), String.valueOf(value)));
    return map;
  }

  /**
   * Applies defaults to the declarative configuration model under {@code
   * instrumentation/development.java}. Existing values in the model take precedence; defaults are
   * only set for properties not already present.
   */
  @CanIgnoreReturnValue
  public OpenTelemetryConfigurationModel applyToModel(OpenTelemetryConfigurationModel model) {
    return DefaultInstrumentationConfigApplier.applyToModel(this, model);
  }

  Map<String, Object> getDefaults() {
    return defaults;
  }

  private String pathWithName(String name) {
    if (path.isEmpty()) {
      return name;
    }
    return String.join(".", path) + "." + name;
  }

  private String toConfigProperty(String declarativePath) {
    String propertyPrefix = null;
    String declarativePrefix = null;
    for (Map.Entry<String, String> entry : propertyMappings.entrySet()) {
      String candidate = entry.getValue();
      if (!matchesPrefix(declarativePath, candidate)) {
        continue;
      }
      if (declarativePrefix == null || candidate.length() > declarativePrefix.length()) {
        declarativePrefix = candidate;
        propertyPrefix = entry.getKey();
      }
    }

    if (propertyPrefix == null) {
      return ConfigPropertiesBackedDeclarativeConfigProperties.toPropertyKey(
          "java." + declarativePath);
    }
    if (declarativePrefix == null) {
      throw new IllegalStateException("missing declarative prefix for property mapping");
    }

    if (declarativePath.equals(declarativePrefix)) {
      return propertyPrefix;
    }

    int matchedPrefixLength = declarativePrefix.length();
    return propertyPrefix + "." + translatePath(declarativePath.substring(matchedPrefixLength + 1));
  }

  @CanIgnoreReturnValue
  private DefaultInstrumentationConfig setDefaultValue(String key, Object value) {
    defaults.put(pathWithName(key), value);
    return this;
  }

  private static boolean matchesPrefix(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + ".");
  }

  private static String translatePath(String path) {
    String[] segments = path.split("\\.");
    StringBuilder translated = new StringBuilder();
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        translated.append(".");
      }
      translated.append(translateName(segments[i]));
    }
    return translated.toString();
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
