/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigException;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * A {@link ConfigProperties} which resolves properties based on {@link
 * DeclarativeConfigProperties}.
 *
 * <p>Only properties starting with "otel.instrumentation." are resolved. Others return null (or
 * default value if provided).
 *
 * <p>To resolve:
 *
 * <ul>
 *   <li>"otel.instrumentation" refers to the ".instrumentation.java" node
 *   <li>The portion of the property after "otel.instrumentation." is split into segments based on
 *       ".".
 *   <li>For each N-1 segment, we walk down the tree to find the relevant leaf {@link
 *       DeclarativeConfigProperties}.
 *   <li>We extract the property from the resolved {@link DeclarativeConfigProperties} using the
 *       last segment as the property key.
 * </ul>
 *
 * <p>For example, given the following YAML, asking for {@code
 * ConfigProperties#getString("otel.instrumentation.common.string_key")} yields "value":
 *
 * <pre>
 *   instrumentation:
 *     java:
 *       common:
 *         string_key: value
 * </pre>
 */
public final class DeclarativeConfigPropertiesBridge implements ConfigProperties {

  private static final String OTEL_INSTRUMENTATION_PREFIX = "otel.instrumentation.";
  private static final String OTEL_JAVA_AGENT_PREFIX = "otel.javaagent.";

  private static final Map<String, String> JAVA_MAPPING_RULES = new HashMap<>();
  private static final Map<String, String> GENERAL_MAPPING_RULES = new HashMap<>();
  private static final Set<String> AGENT_LOGGING_OUTPUTS =
      new HashSet<>(Arrays.asList("application", "simple"));

  // The node at .instrumentation.java
  private final DeclarativeConfigProperties instrumentationJavaNode;

  private final DeclarativeConfigProperties instrumentationGeneralNode;

  static {
    JAVA_MAPPING_RULES.put("otel.instrumentation.common.default-enabled", "common.default.enabled");
    JAVA_MAPPING_RULES.put(
        "otel.javaagent.logging.application.logs-buffer-max-records",
        "agent.logging.output.application.logs_buffer_max_records");

    // todo not supported in SDK yet (this is strictly typed)
    //    GENERAL_MAPPING_RULES.put("otel.instrumentation.http.known-methods",
    // "http.known_methods");
    GENERAL_MAPPING_RULES.put(
        "otel.instrumentation.http.client.capture-request-headers",
        "http.client.request_captured_headers");
    GENERAL_MAPPING_RULES.put(
        "otel.instrumentation.http.client.capture-response-headers",
        "http.client.response_captured_headers");
    GENERAL_MAPPING_RULES.put(
        "otel.instrumentation.http.server.capture-request-headers",
        "http.server.request_captured_headers");
    GENERAL_MAPPING_RULES.put(
        "otel.instrumentation.http.server.capture-response-headers",
        "http.server.response_captured_headers");
  }

  private final String logLevel;

  private static Map<String, String> getPeerServiceMapping(
      DeclarativeConfigPropertiesBridge bridge) {
    List<DeclarativeConfigProperties> configProperties =
        bridge
            .instrumentationGeneralNode
            .getStructured("peer", empty())
            .getStructuredList("service_mapping", Collections.emptyList());
    return configProperties.stream()
        .collect(
            Collectors.toMap(
                e -> Objects.requireNonNull(e.getString("peer"), "peer must not be null"),
                e -> Objects.requireNonNull(e.getString("service"), "service must not be null")));
  }

  public DeclarativeConfigPropertiesBridge(ConfigProvider configProvider, String logLevel) {
    this.logLevel = logLevel;
    DeclarativeConfigProperties inst = configProvider.getInstrumentationConfig();
    if (inst == null) {
      inst = DeclarativeConfigProperties.empty();
    }
    instrumentationJavaNode = inst.getStructured("java", empty());
    instrumentationGeneralNode = inst.getStructured("general", empty());
  }

  @Nullable
  @Override
  public String getString(String propertyName) {
    if ("otel.javaagent.logging".equals(propertyName)) {
      return agentLoggerName();
    }

    return getPropertyValue(propertyName, DeclarativeConfigProperties::getString);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String propertyName) {
    if ("otel.javaagent.debug".equals(propertyName)) {
      return "DEBUG".equals(this.logLevel);
    }

    return getPropertyValue(propertyName, DeclarativeConfigProperties::getBoolean);
  }

  @Nullable
  @Override
  public Integer getInt(String propertyName) {
    return getPropertyValue(propertyName, DeclarativeConfigProperties::getInt);
  }

  @Nullable
  @Override
  public Long getLong(String propertyName) {
    return getPropertyValue(propertyName, DeclarativeConfigProperties::getLong);
  }

  @Nullable
  @Override
  public Double getDouble(String propertyName) {
    return getPropertyValue(propertyName, DeclarativeConfigProperties::getDouble);
  }

  @Nullable
  @Override
  public Duration getDuration(String propertyName) {
    Long millis = getPropertyValue(propertyName, DeclarativeConfigProperties::getLong);
    if (millis == null) {
      return null;
    }
    return Duration.ofMillis(millis);
  }

  @Override
  public List<String> getList(String propertyName) {
    List<String> propertyValue =
        getPropertyValue(
            propertyName,
            (properties, lastPart) -> properties.getScalarList(lastPart, String.class));
    return propertyValue == null ? Collections.emptyList() : propertyValue;
  }

  @Override
  public Map<String, String> getMap(String propertyName) {
    if ("otel.instrumentation.common.peer-service-mapping".equals(propertyName)) {
      return getPeerServiceMapping(this);
    }

    DeclarativeConfigProperties propertyValue =
        getPropertyValue(propertyName, DeclarativeConfigProperties::getStructured);
    if (propertyValue == null) {
      return Collections.emptyMap();
    }
    Map<String, String> result = new HashMap<>();
    propertyValue
        .getPropertyKeys()
        .forEach(
            key -> {
              String value = propertyValue.getString(key);
              if (value == null) {
                return;
              }
              result.put(key, value);
            });
    return Collections.unmodifiableMap(result);
  }

  @Nullable
  private <T> T getPropertyValue(
      String property, BiFunction<DeclarativeConfigProperties, String, T> extractor) {
    String generalPath = GENERAL_MAPPING_RULES.get(property);
    if (generalPath != null) {
      return splitOnDot(generalPath, instrumentationGeneralNode, extractor);
    }
    String javaPath = getJavaPath(property);
    if (javaPath != null) {
      return splitOnDot(javaPath, instrumentationJavaNode, extractor);
    }
    return null;
  }

  private static <T> T splitOnDot(
      String path,
      DeclarativeConfigProperties target,
      BiFunction<DeclarativeConfigProperties, String, T> extractor) {
    // Split the remainder of the property on ".", and walk to the N-1 entry
    String[] segments = path.split("\\.");
    if (segments.length == 0) {
      return null;
    }
    if (segments.length > 1) {
      for (int i = 0; i < segments.length - 1; i++) {
        target = target.getStructured(segments[i], empty());
      }
    }
    String lastPart = segments[segments.length - 1];
    return extractor.apply(target, lastPart);
  }

  private static String getJavaPath(String property) {
    String special = JAVA_MAPPING_RULES.get(property);
    if (special != null) {
      return special;
    }

    if (property.startsWith(OTEL_INSTRUMENTATION_PREFIX)) {
      return property.substring(OTEL_INSTRUMENTATION_PREFIX.length()).replace('-', '_');
    } else if (property.startsWith(OTEL_JAVA_AGENT_PREFIX)) {
      return "agent." + property.substring(OTEL_JAVA_AGENT_PREFIX.length()).replace('-', '_');
    }
    return null;
  }

  @Nullable
  private String agentLoggerName() {
    DeclarativeConfigProperties logOutput = getLogOutput();
    Set<String> names = logOutput.getPropertyKeys();

    if (names.isEmpty()) {
      // no log output configured
      return null;
    }

    if (names.size() > 1) {
      throw new DeclarativeConfigException(
          "Multiple log output formats are configured: "
              + String.join(", ", names)
              + ". Please choose one of them.");
    }

    String name = names.iterator().next();
    if (!AGENT_LOGGING_OUTPUTS.contains(name)) {
      throw new DeclarativeConfigException(
          "Unsupported log output format: '"
              + name
              + "' . Supported formats are: "
              + String.join(", ", AGENT_LOGGING_OUTPUTS));
    }
    return name;
  }

  private DeclarativeConfigProperties getLogOutput() {
    return getAgent().getStructured("logging", empty()).getStructured("output", empty());
  }

  private DeclarativeConfigProperties getAgent() {
    return instrumentationJavaNode.getStructured("agent", empty());
  }
}
