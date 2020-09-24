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

package io.opentelemetry.instrumentation.api.config;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import io.grpc.Context;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.SortedSet;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoValue
public abstract class Config {
  private static final Logger log = LoggerFactory.getLogger(Config.class);
  private static final Pattern PROPERTY_NAME_REPLACEMENTS = Pattern.compile("[^a-zA-Z0-9.]");

  // locations where the context was propagated to another thread (tracking multiple steps is
  // helpful in akka where there is so much recursive async spawning of new work)
  public static final Context.Key<List<StackTraceElement[]>> THREAD_PROPAGATION_LOCATIONS =
      Context.key("thread-propagation-locations");
  public static final boolean THREAD_PROPAGATION_DEBUGGER =
      Boolean.getBoolean("otel.threadPropagationDebugger");

  public static final String DEFAULT_EXPORTER = "otlp";
  public static final boolean DEFAULT_TRACE_ENABLED = true;
  public static final boolean DEFAULT_INTEGRATIONS_ENABLED = true;
  public static final boolean DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION = true;
  public static final boolean DEFAULT_TRACE_EXECUTORS_ALL = false;
  public static final boolean DEFAULT_SQL_NORMALIZER_ENABLED = true;
  public static final boolean DEFAULT_KAFKA_CLIENT_PROPAGATION_ENABLED = true;
  public static final boolean DEFAULT_HYSTRIX_TAGS_ENABLED = false;

  private static final Config DEFAULT =
      Config.newBuilder()
          .setAllProperties(Collections.emptyMap())
          .setExporterJar(Optional.empty())
          .setExporter(DEFAULT_EXPORTER)
          .setPropagators(Collections.emptyList())
          .setTraceEnabled(DEFAULT_TRACE_ENABLED)
          .setIntegrationsEnabled(DEFAULT_INTEGRATIONS_ENABLED)
          .setExcludedClasses(Collections.emptyList())
          .setRuntimeContextFieldInjection(DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION)
          .setTraceAnnotations(Optional.empty())
          .setTraceMethods("")
          .setTraceAnnotatedMethodsExclude("")
          .setTraceExecutorsAll(DEFAULT_TRACE_EXECUTORS_ALL)
          .setTraceExecutors(Collections.emptyList())
          .setSqlNormalizerEnabled(DEFAULT_SQL_NORMALIZER_ENABLED)
          .setKafkaClientPropagationEnabled(DEFAULT_KAFKA_CLIENT_PROPAGATION_ENABLED)
          .setHystrixTagsEnabled(DEFAULT_HYSTRIX_TAGS_ENABLED)
          .setEndpointPeerServiceMapping(Collections.emptyMap())
          .build();

  // INSTANCE can never be null - muzzle instantiates instrumenters when it generates
  // getInstrumentationMuzzle() and the Instrumenter.Default constructor uses Config
  private static volatile Config INSTANCE = DEFAULT;

  /**
   * Sets the agent configuration singleton. This method is only supposed to be called once, from
   * the agent classloader just before the first instrumentation is loaded (and before {@link
   * Config#get()} is used for the first time).
   */
  public static void internalInitializeConfig(Config config) {
    if (INSTANCE != DEFAULT) {
      log.warn("Config#INSTANCE was already set earlier");
      return;
    }
    INSTANCE = requireNonNull(config);
  }

  public static Config get() {
    return INSTANCE;
  }

  public abstract Map<String, String> getAllProperties();

  @Nullable
  public String getProperty(String propertyName) {
    return getAllProperties().get(normalizePropertyName(propertyName));
  }

  // some integrations have '-' or '_' character in their names -- this does not work well with
  // environment variables (where we replace every non-alphanumeric character with '.'), so we're
  // replacing those with a dot
  public static String normalizePropertyName(String propertyName) {
    return PROPERTY_NAME_REPLACEMENTS.matcher(propertyName.toLowerCase()).replaceAll(".");
  }

  public boolean isIntegrationEnabled(SortedSet<String> integrationNames, boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (String name : integrationNames) {
      String enabledPropertyValue = getProperty("otel.integration." + name + ".enabled");
      boolean configEnabled =
          enabledPropertyValue == null
              ? defaultEnabled
              : Boolean.parseBoolean(enabledPropertyValue);

      if (defaultEnabled) {
        anyEnabled &= configEnabled;
      } else {
        anyEnabled |= configEnabled;
      }
    }
    return anyEnabled;
  }

  public Properties asJavaProperties() {
    Properties properties = new Properties();
    properties.putAll(getAllProperties());
    return properties;
  }

  public abstract Optional<String> getExporterJar();

  public abstract String getExporter();

  public abstract List<String> getPropagators();

  abstract boolean getTraceEnabled();

  public boolean isTraceEnabled() {
    return getTraceEnabled();
  }

  abstract boolean getIntegrationsEnabled();

  public boolean isIntegrationsEnabled() {
    return getIntegrationsEnabled();
  }

  public abstract List<String> getExcludedClasses();

  abstract boolean getRuntimeContextFieldInjection();

  public boolean isRuntimeContextFieldInjection() {
    return getRuntimeContextFieldInjection();
  }

  public abstract Optional<String> getTraceAnnotations();

  public abstract String getTraceMethods();

  public abstract String getTraceAnnotatedMethodsExclude();

  abstract boolean getTraceExecutorsAll();

  public boolean isTraceExecutorsAll() {
    return getTraceExecutorsAll();
  }

  public abstract List<String> getTraceExecutors();

  abstract boolean getSqlNormalizerEnabled();

  public boolean isSqlNormalizerEnabled() {
    return getSqlNormalizerEnabled();
  }

  abstract boolean getKafkaClientPropagationEnabled();

  public boolean isKafkaClientPropagationEnabled() {
    return getKafkaClientPropagationEnabled();
  }

  abstract boolean getHystrixTagsEnabled();

  public boolean isHystrixTagsEnabled() {
    return getHystrixTagsEnabled();
  }

  public abstract Map<String, String> getEndpointPeerServiceMapping();

  public static Config.Builder newBuilder() {
    return new AutoValue_Config.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setAllProperties(Map<String, String> allProperties);

    public abstract Builder setExporterJar(Optional<String> exporterJar);

    public abstract Builder setExporter(String exporter);

    public abstract Builder setPropagators(List<String> propagators);

    public abstract Builder setTraceEnabled(boolean traceEnabled);

    public abstract Builder setIntegrationsEnabled(boolean integrationsEnabled);

    public abstract Builder setExcludedClasses(List<String> excludedClasses);

    public abstract Builder setRuntimeContextFieldInjection(boolean runtimeContextFieldInjection);

    public abstract Builder setTraceAnnotations(Optional<String> traceAnnotations);

    public abstract Builder setTraceMethods(String traceMethods);

    public abstract Builder setTraceAnnotatedMethodsExclude(String traceAnnotatedMethodsExclude);

    public abstract Builder setTraceExecutorsAll(boolean traceExecutorsAll);

    public abstract Builder setTraceExecutors(List<String> traceExecutors);

    public abstract Builder setSqlNormalizerEnabled(boolean sqlNormalizerEnabled);

    public abstract Builder setKafkaClientPropagationEnabled(boolean kafkaClientPropagationEnabled);

    public abstract Builder setHystrixTagsEnabled(boolean hystrixTagsEnabled);

    public abstract Builder setEndpointPeerServiceMapping(
        Map<String, String> endpointPeerServiceMapping);

    public abstract Config build();
  }
}
