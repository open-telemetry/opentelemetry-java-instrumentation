/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.util.Collections.emptySet;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.time.Duration;
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

  private static final String AGENT_INSTRUMENTATION_MODE = "java.agent.instrumentation_mode";
  private static final String SPRING_STARTER_INSTRUMENTATION_MODE =
      "java.spring_starter.instrumentation_mode";
  private static final String COMMON_DEFAULT_ENABLED =
      "otel.instrumentation.common.default-enabled";

  private static final Map<String, String> SPECIAL_MAPPINGS;

  static {
    SPECIAL_MAPPINGS = new HashMap<>();
    // mapping of general configs to old property names
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
    // moving common http, database, messaging, and gen_ai configs under common
    SPECIAL_MAPPINGS.put(
        "java.common.http.known_methods", "otel.instrumentation.http.known-methods");
    SPECIAL_MAPPINGS.put(
        "java.common.http.client.redact_query_parameters/development",
        "otel.instrumentation.http.client.experimental.redact-query-parameters");
    SPECIAL_MAPPINGS.put(
        "java.common.http.client.emit_experimental_telemetry/development",
        "otel.instrumentation.http.client.emit-experimental-telemetry");
    SPECIAL_MAPPINGS.put(
        "java.common.http.server.emit_experimental_telemetry/development",
        "otel.instrumentation.http.server.emit-experimental-telemetry");
    SPECIAL_MAPPINGS.put(
        "java.common.database.statement_sanitizer.enabled",
        "otel.instrumentation.common.db-statement-sanitizer.enabled");
    SPECIAL_MAPPINGS.put(
        "java.common.database.sqlcommenter/development.enabled",
        "otel.instrumentation.common.experimental.db-sqlcommenter.enabled");
    SPECIAL_MAPPINGS.put(
        "java.common.messaging.receive_telemetry/development.enabled",
        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled");
    SPECIAL_MAPPINGS.put(
        "java.common.messaging.capture_headers/development",
        "otel.instrumentation.messaging.experimental.capture-headers");
    SPECIAL_MAPPINGS.put(
        "java.common.gen_ai.capture_message_content",
        "otel.instrumentation.genai.capture-message-content");
    // top-level common configs
    SPECIAL_MAPPINGS.put(
        "java.common.span_suppression_strategy/development",
        "otel.instrumentation.experimental.span-suppression-strategy");
    // renaming to match instrumentation module name
    SPECIAL_MAPPINGS.put(
        "java.opentelemetry_extension_annotations.exclude_methods",
        "otel.instrumentation.opentelemetry-annotations.exclude-methods");
    // renaming to avoid top level config
    SPECIAL_MAPPINGS.put(
        "java.servlet.javascript_snippet/development", "otel.experimental.javascript-snippet");
    // jmx properties don't have an "instrumentation" segment
    SPECIAL_MAPPINGS.put("java.jmx.enabled", "otel.jmx.enabled");
    SPECIAL_MAPPINGS.put("java.jmx.config", "otel.jmx.config");
    SPECIAL_MAPPINGS.put("java.jmx.target_system", "otel.jmx.target.system");
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

    if (fullPath.equals(AGENT_INSTRUMENTATION_MODE)
        || fullPath.equals(SPRING_STARTER_INSTRUMENTATION_MODE)) {
      Boolean value = configProperties.getBoolean(COMMON_DEFAULT_ENABLED);
      if (value != null) {
        return value ? "default" : "none";
      }
      return null;
    }

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
    String fullPath = pathWithName(name);

    if (fullPath.equals("java.jmx.discovery.delay")) {
      Duration duration = configProperties.getDuration("otel.jmx.discovery.delay");
      if (duration != null) {
        return duration.toMillis();
      }
      // If discovery delay has not been configured, have a peek at the metric export interval.
      // It makes sense for both of these values to be similar.
      Duration fallback = configProperties.getDuration("otel.metric.export.interval");
      if (fallback != null) {
        return fallback.toMillis();
      }
      return null;
    }

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
