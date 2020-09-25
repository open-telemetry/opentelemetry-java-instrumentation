/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.instrumentation.api.config.Config.DEFAULT_EXPORTER;
import static io.opentelemetry.instrumentation.api.config.Config.DEFAULT_HYSTRIX_TAGS_ENABLED;
import static io.opentelemetry.instrumentation.api.config.Config.DEFAULT_INTEGRATIONS_ENABLED;
import static io.opentelemetry.instrumentation.api.config.Config.DEFAULT_KAFKA_CLIENT_PROPAGATION_ENABLED;
import static io.opentelemetry.instrumentation.api.config.Config.DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION;
import static io.opentelemetry.instrumentation.api.config.Config.DEFAULT_SQL_NORMALIZER_ENABLED;
import static io.opentelemetry.instrumentation.api.config.Config.DEFAULT_TRACE_ENABLED;
import static io.opentelemetry.instrumentation.api.config.Config.DEFAULT_TRACE_EXECUTORS_ALL;
import static io.opentelemetry.instrumentation.api.config.Config.normalizePropertyName;

import io.opentelemetry.instrumentation.api.config.Config;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigBuilder
    extends io.opentelemetry.sdk.common.export.ConfigBuilder<ConfigBuilder> {
  private static final Logger log = LoggerFactory.getLogger(ConfigBuilder.class);

  public static final String EXPORTER_JAR = "otel.exporter.jar";
  public static final String EXPORTER = "otel.exporter";
  public static final String PROPAGATORS = "otel.propagators";
  public static final String TRACE_ENABLED = "otel.trace.enabled";
  public static final String INTEGRATIONS_ENABLED = "otel.integrations.enabled";
  public static final String TRACE_CLASSES_EXCLUDE = "otel.trace.classes.exclude";
  public static final String RUNTIME_CONTEXT_FIELD_INJECTION =
      "otel.trace.runtime.context.field.injection";
  public static final String TRACE_ANNOTATIONS = "otel.trace.annotations";
  public static final String TRACE_METHODS = "otel.trace.methods";
  public static final String TRACE_ANNOTATED_METHODS_EXCLUDE =
      "otel.trace.annotated.methods.exclude";
  public static final String TRACE_EXECUTORS_ALL = "otel.trace.executors.all";
  public static final String TRACE_EXECUTORS = "otel.trace.executors";
  public static final String SQL_NORMALIZER_ENABLED = "sql.normalizer.enabled";
  public static final String KAFKA_CLIENT_PROPAGATION_ENABLED =
      "otel.kafka.client.propagation.enabled";
  public static final String HYSTRIX_TAGS_ENABLED = "otel.hystrix.tags.enabled";
  public static final String ENDPOINT_PEER_SERVICE_MAPPING = "otel.endpoint.peer.service.mapping";

  private final Map<String, String> allProperties = new HashMap<>();

  @Override
  public ConfigBuilder readProperties(Properties properties) {
    return this.fromConfigMap(normalizedProperties(properties), NamingConvention.DOT);
  }

  private static Map<String, String> normalizedProperties(Properties properties) {
    Map<String, String> configMap = new HashMap<>(properties.size());
    for (Enumeration<?> e = properties.propertyNames(); e.hasMoreElements(); ) {
      String propertyName = (String) e.nextElement();
      configMap.put(normalizePropertyName(propertyName), properties.getProperty(propertyName));
    }
    return configMap;
  }

  ConfigBuilder readPropertiesFromAllSources(Properties configurationFile) {
    // ordering from least to most important
    return readProperties(configurationFile).readEnvironmentVariables().readSystemProperties();
  }

  @Override
  protected ConfigBuilder fromConfigMap(
      Map<String, String> configMap, NamingConvention namingConvention) {
    configMap = namingConvention.normalize(configMap);
    allProperties.putAll(configMap);
    return this;
  }

  Config build() {
    return Config.newBuilder()
        .setAllProperties(allProperties)
        .setExporterJar(getProperty(EXPORTER_JAR))
        .setExporter(getProperty(EXPORTER, DEFAULT_EXPORTER))
        .setPropagators(getListProperty(PROPAGATORS))
        .setTraceEnabled(getBooleanProperty(TRACE_ENABLED, DEFAULT_TRACE_ENABLED))
        .setIntegrationsEnabled(
            getBooleanProperty(INTEGRATIONS_ENABLED, DEFAULT_INTEGRATIONS_ENABLED))
        .setExcludedClasses(getListProperty(TRACE_CLASSES_EXCLUDE))
        .setRuntimeContextFieldInjection(
            getBooleanProperty(
                RUNTIME_CONTEXT_FIELD_INJECTION, DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION))
        .setTraceAnnotations(getProperty(TRACE_ANNOTATIONS))
        .setTraceMethods(getProperty(TRACE_METHODS))
        .setTraceAnnotatedMethodsExclude(getProperty(TRACE_ANNOTATED_METHODS_EXCLUDE))
        .setTraceExecutorsAll(getBooleanProperty(TRACE_EXECUTORS_ALL, DEFAULT_TRACE_EXECUTORS_ALL))
        .setTraceExecutors(getListProperty(TRACE_EXECUTORS))
        .setSqlNormalizerEnabled(
            getBooleanProperty(SQL_NORMALIZER_ENABLED, DEFAULT_SQL_NORMALIZER_ENABLED))
        .setKafkaClientPropagationEnabled(
            getBooleanProperty(
                KAFKA_CLIENT_PROPAGATION_ENABLED, DEFAULT_KAFKA_CLIENT_PROPAGATION_ENABLED))
        .setHystrixTagsEnabled(
            getBooleanProperty(HYSTRIX_TAGS_ENABLED, DEFAULT_HYSTRIX_TAGS_ENABLED))
        .setEndpointPeerServiceMapping(getMapProperty(ENDPOINT_PEER_SERVICE_MAPPING))
        .build();
  }

  @Nullable
  private String getProperty(String name) {
    return getProperty(name, null);
  }

  private String getProperty(String name, String defaultValue) {
    return allProperties.getOrDefault(name, defaultValue);
  }

  private boolean getBooleanProperty(String name, boolean defaultValue) {
    return getProperty(name, Boolean::parseBoolean, defaultValue);
  }

  private List<String> getListProperty(String name) {
    return getProperty(name, ConfigBuilder::parseList, Collections.emptyList());
  }

  private Map<String, String> getMapProperty(String name) {
    return getProperty(name, ConfigBuilder::parseMap, Collections.emptyMap());
  }

  private <T> T getProperty(String name, Function<String, T> parser, T defaultValue) {
    String value = getProperty(name);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return parser.apply(value);
    } catch (Throwable t) {
      log.debug("Cannot parse {}", value, t);
      return defaultValue;
    }
  }

  private static List<String> parseList(String value) {
    String[] tokens = value.split(",", -1);
    // Remove whitespace from each item.
    for (int i = 0; i < tokens.length; i++) {
      tokens[i] = tokens[i].trim();
    }
    return Collections.unmodifiableList(Arrays.asList(tokens));
  }

  private static Map<String, String> parseMap(String value) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String token : value.split(",", -1)) {
      token = token.trim();
      String[] parts = token.split("=", -1);
      if (parts.length != 2) {
        log.warn("Invalid map config part, should be formatted key1=value1,key2=value2: {}", value);
        return Collections.emptyMap();
      }
      result.put(parts[0], parts[1]);
    }
    return Collections.unmodifiableMap(result);
  }
}
