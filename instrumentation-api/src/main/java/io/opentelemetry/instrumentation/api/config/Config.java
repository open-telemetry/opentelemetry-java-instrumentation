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

import io.grpc.Context;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config reads values with the following priority: 1) system properties, 2) environment variables,
 * 3) optional configuration file. It also includes default values to ensure a valid config.
 *
 * <p>
 *
 * <p>System properties are {@link Config#PREFIX}'ed. Environment variables are the same as the
 * system property, but uppercased and '.' is replaced with '_'.
 */
public class Config {

  private static final Logger log = LoggerFactory.getLogger(Config.class);

  // locations where the context was propagated to another thread (tracking multiple steps is
  // helpful in akka where there is so much recursive async spawning of new work)
  public static final Context.Key<List<StackTraceElement[]>> THREAD_PROPAGATION_LOCATIONS =
      Context.key("thread-propagation-locations");
  public static final boolean THREAD_PROPAGATION_DEBUGGER =
      Boolean.getBoolean("otel.threadPropagationDebugger");

  private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();

  /** Config keys below */
  private static final String PREFIX = "otel.";

  private static final Pattern ENV_REPLACEMENT = Pattern.compile("[^a-zA-Z0-9_]");

  public static final String EXPORTER_JAR = "exporter.jar";
  public static final String EXPORTER = "exporter";
  public static final String PROPAGATORS = "propagators";
  public static final String CONFIGURATION_FILE = "trace.config";
  public static final String TRACE_ENABLED = "trace.enabled";
  public static final String INTEGRATIONS_ENABLED = "integrations.enabled";
  public static final String TRACE_ANNOTATIONS = "trace.annotations";
  public static final String TRACE_EXECUTORS_ALL = "trace.executors.all";
  public static final String TRACE_EXECUTORS = "trace.executors";
  public static final String TRACE_METHODS = "trace.methods";
  public static final String TRACE_ANNOTATED_METHODS_EXCLUDE = "trace.annotated.methods.exclude";
  public static final String TRACE_CLASSES_EXCLUDE = "trace.classes.exclude";
  public static final String HTTP_SERVER_ERROR_STATUSES = "http.server.error.statuses";
  public static final String HTTP_CLIENT_ERROR_STATUSES = "http.client.error.statuses";
  public static final String HTTP_SERVER_TAG_QUERY_STRING = "http.server.tag.query-string";
  public static final String HTTP_CLIENT_TAG_QUERY_STRING = "http.client.tag.query-string";
  public static final String SCOPE_DEPTH_LIMIT = "trace.scope.depth.limit";
  public static final String RUNTIME_CONTEXT_FIELD_INJECTION =
      "trace.runtime.context.field.injection";

  public static final String KAFKA_CLIENT_PROPAGATION_ENABLED = "kafka.client.propagation.enabled";

  public static final String HYSTRIX_TAGS_ENABLED = "hystrix.tags.enabled";

  public static final String ENDPOINT_PEER_SERVICE_MAPPING = "endpoint.peer.service.mapping";

  private static final boolean DEFAULT_TRACE_ENABLED = true;
  public static final boolean DEFAULT_INTEGRATIONS_ENABLED = true;

  private static final boolean DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION = true;

  private static final boolean DEFAULT_HTTP_SERVER_TAG_QUERY_STRING = false;
  private static final boolean DEFAULT_HTTP_CLIENT_TAG_QUERY_STRING = false;
  private static final int DEFAULT_SCOPE_DEPTH_LIMIT = 100;

  public static final boolean DEFAULT_LOG_INJECTION_ENABLED = false;
  public static final String DEFAULT_EXPERIMENTAL_LOG_CAPTURE_THRESHOLD = null;

  public static final boolean DEFAULT_KAFKA_CLIENT_PROPAGATION_ENABLED = true;

  public static final boolean DEFAULT_HYSTRIX_TAGS_ENABLED = false;

  private static final String DEFAULT_TRACE_ANNOTATIONS = null;
  private static final boolean DEFAULT_TRACE_EXECUTORS_ALL = false;
  private static final String DEFAULT_TRACE_EXECUTORS = "";
  private static final String DEFAULT_TRACE_METHODS = null;
  private static final String DEFAULT_TRACE_ANNOTATED_METHODS_EXCLUDE = null;

  public static final String SQL_NORMALIZER_ENABLED = "sql.normalizer.enabled";
  public static final boolean DEFAULT_SQL_NORMALIZER_ENABLED = true;

  private final String exporterJar;
  private final String exporter;
  private final List<String> propagators;
  private final boolean traceEnabled;
  private final boolean integrationsEnabled;
  private final List<String> excludedClasses;
  private final boolean httpServerTagQueryString;
  private final boolean httpClientTagQueryString;
  private final Integer scopeDepthLimit;
  private final boolean runtimeContextFieldInjection;

  private final String traceAnnotations;

  private final String traceMethods;
  private final String traceAnnotatedMethodsExclude;

  private final boolean traceExecutorsAll;
  private final List<String> traceExecutors;

  private final boolean sqlNormalizerEnabled;

  private final boolean kafkaClientPropagationEnabled;

  private final boolean hystrixTagsEnabled;

  private final Map<String, String> endpointPeerServiceMapping;

  // Values from an optionally provided properties file
  private static Properties propertiesFromConfigFile;

  // Read order: System Properties -> Env Variables, [-> properties file], [-> default value]
  // Visible for testing
  Config() {
    propertiesFromConfigFile = loadConfigurationFile();

    propagators = getListSettingFromEnvironment(PROPAGATORS, null);
    exporterJar = getSettingFromEnvironment(EXPORTER_JAR, null);
    exporter = getSettingFromEnvironment(EXPORTER, "otlp");
    traceEnabled = getBooleanSettingFromEnvironment(TRACE_ENABLED, DEFAULT_TRACE_ENABLED);
    integrationsEnabled =
        getBooleanSettingFromEnvironment(INTEGRATIONS_ENABLED, DEFAULT_INTEGRATIONS_ENABLED);

    excludedClasses = getListSettingFromEnvironment(TRACE_CLASSES_EXCLUDE, null);

    httpServerTagQueryString =
        getBooleanSettingFromEnvironment(
            HTTP_SERVER_TAG_QUERY_STRING, DEFAULT_HTTP_SERVER_TAG_QUERY_STRING);

    httpClientTagQueryString =
        getBooleanSettingFromEnvironment(
            HTTP_CLIENT_TAG_QUERY_STRING, DEFAULT_HTTP_CLIENT_TAG_QUERY_STRING);

    scopeDepthLimit =
        getIntegerSettingFromEnvironment(SCOPE_DEPTH_LIMIT, DEFAULT_SCOPE_DEPTH_LIMIT);

    runtimeContextFieldInjection =
        getBooleanSettingFromEnvironment(
            RUNTIME_CONTEXT_FIELD_INJECTION, DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION);

    traceAnnotations = getSettingFromEnvironment(TRACE_ANNOTATIONS, DEFAULT_TRACE_ANNOTATIONS);

    traceMethods = getSettingFromEnvironment(TRACE_METHODS, DEFAULT_TRACE_METHODS);
    traceAnnotatedMethodsExclude =
        getSettingFromEnvironment(
            TRACE_ANNOTATED_METHODS_EXCLUDE, DEFAULT_TRACE_ANNOTATED_METHODS_EXCLUDE);

    traceExecutorsAll =
        getBooleanSettingFromEnvironment(TRACE_EXECUTORS_ALL, DEFAULT_TRACE_EXECUTORS_ALL);

    traceExecutors = getListSettingFromEnvironment(TRACE_EXECUTORS, DEFAULT_TRACE_EXECUTORS);

    sqlNormalizerEnabled =
        getBooleanSettingFromEnvironment(SQL_NORMALIZER_ENABLED, DEFAULT_SQL_NORMALIZER_ENABLED);

    kafkaClientPropagationEnabled =
        getBooleanSettingFromEnvironment(
            KAFKA_CLIENT_PROPAGATION_ENABLED, DEFAULT_KAFKA_CLIENT_PROPAGATION_ENABLED);

    hystrixTagsEnabled =
        getBooleanSettingFromEnvironment(HYSTRIX_TAGS_ENABLED, DEFAULT_HYSTRIX_TAGS_ENABLED);

    endpointPeerServiceMapping = getMapSettingFromEnvironment(ENDPOINT_PEER_SERVICE_MAPPING);

    log.debug("New instance: {}", this);
  }

  // Read order: Properties -> Parent
  private Config(Properties properties, Config parent) {
    exporterJar = properties.getProperty(EXPORTER_JAR, parent.exporterJar);
    exporter = properties.getProperty(EXPORTER, parent.exporter);

    propagators = getPropertyListValue(properties, PROPAGATORS, parent.propagators);

    traceEnabled = getPropertyBooleanValue(properties, TRACE_ENABLED, parent.traceEnabled);
    integrationsEnabled =
        getPropertyBooleanValue(properties, INTEGRATIONS_ENABLED, parent.integrationsEnabled);

    excludedClasses =
        getPropertyListValue(properties, TRACE_CLASSES_EXCLUDE, parent.excludedClasses);

    httpServerTagQueryString =
        getPropertyBooleanValue(
            properties, HTTP_SERVER_TAG_QUERY_STRING, parent.httpServerTagQueryString);

    httpClientTagQueryString =
        getPropertyBooleanValue(
            properties, HTTP_CLIENT_TAG_QUERY_STRING, parent.httpClientTagQueryString);

    scopeDepthLimit =
        getPropertyIntegerValue(properties, SCOPE_DEPTH_LIMIT, parent.scopeDepthLimit);

    runtimeContextFieldInjection =
        getPropertyBooleanValue(
            properties, RUNTIME_CONTEXT_FIELD_INJECTION, parent.runtimeContextFieldInjection);

    traceAnnotations = properties.getProperty(TRACE_ANNOTATIONS, parent.traceAnnotations);

    traceMethods = properties.getProperty(TRACE_METHODS, parent.traceMethods);
    traceAnnotatedMethodsExclude =
        properties.getProperty(
            TRACE_ANNOTATED_METHODS_EXCLUDE, parent.traceAnnotatedMethodsExclude);

    traceExecutorsAll =
        getPropertyBooleanValue(properties, TRACE_EXECUTORS_ALL, parent.traceExecutorsAll);
    traceExecutors = getPropertyListValue(properties, TRACE_EXECUTORS, parent.traceExecutors);

    sqlNormalizerEnabled =
        getPropertyBooleanValue(properties, SQL_NORMALIZER_ENABLED, parent.sqlNormalizerEnabled);

    kafkaClientPropagationEnabled =
        getPropertyBooleanValue(
            properties, KAFKA_CLIENT_PROPAGATION_ENABLED, parent.kafkaClientPropagationEnabled);

    hystrixTagsEnabled =
        getBooleanSettingFromEnvironment(HYSTRIX_TAGS_ENABLED, parent.hystrixTagsEnabled);

    endpointPeerServiceMapping =
        getPropertyMapValue(
            properties, ENDPOINT_PEER_SERVICE_MAPPING, parent.endpointPeerServiceMapping);

    log.debug("New instance: {}", this);
  }

  public boolean isIntegrationEnabled(SortedSet<String> integrationNames, boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (String name : integrationNames) {
      boolean configEnabled =
          getBooleanSettingFromEnvironment("integration." + name + ".enabled", defaultEnabled);
      if (defaultEnabled) {
        anyEnabled &= configEnabled;
      } else {
        anyEnabled |= configEnabled;
      }
    }
    return anyEnabled;
  }

  /**
   * Helper method that takes the name, adds a "otel." prefix then checks for System Properties of
   * that name. If none found, the name is converted to an Environment Variable and used to check
   * the env. If none of the above returns a value, then an optional properties file if checked. If
   * setting is not configured in either location, <code>defaultValue</code> is returned.
   *
   * @param name
   * @param defaultValue
   * @return
   * @deprecated This method should only be used internally. Use the explicit getter instead.
   */
  public static String getSettingFromEnvironment(String name, String defaultValue) {
    String value;
    String systemPropertyName = propertyNameToSystemPropertyName(name);

    // System properties and properties provided from command line have the highest precedence
    value = System.getProperties().getProperty(systemPropertyName);
    if (null != value) {
      return value;
    }

    // If value not provided from system properties, looking at env variables
    value = System.getenv(propertyNameToEnvironmentVariableName(name));
    if (null != value) {
      return value;
    }

    // If value is not defined yet, we look at properties optionally defined in a properties file
    value = propertiesFromConfigFile.getProperty(systemPropertyName);
    if (null != value) {
      return value;
    }

    return defaultValue;
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a list by
   * splitting on `,`.
   */
  private static List<String> getListSettingFromEnvironment(String name, String defaultValue) {
    return parseList(getSettingFromEnvironment(name, defaultValue));
  }

  private static Map<String, String> getMapSettingFromEnvironment(String name) {
    return parseMap(getSettingFromEnvironment(name, null));
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a Boolean.
   */
  private static Boolean getBooleanSettingFromEnvironment(String name, Boolean defaultValue) {
    return getSettingFromEnvironmentWithLog(name, Boolean.class, defaultValue);
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a Integer.
   */
  private static Integer getIntegerSettingFromEnvironment(String name, Integer defaultValue) {
    return getSettingFromEnvironmentWithLog(name, Integer.class, defaultValue);
  }

  private static <T> T getSettingFromEnvironmentWithLog(
      String name, Class<T> tClass, T defaultValue) {
    try {
      return valueOf(getSettingFromEnvironment(name, null), tClass, defaultValue);
    } catch (NumberFormatException e) {
      log.warn("Invalid configuration for " + name, e);
      return defaultValue;
    }
  }

  /**
   * Converts the property name, e.g. 'trace.enabled' into a public environment variable name, e.g.
   * `OTEL_TRACE_ENABLED`.
   *
   * @param setting The setting name, e.g. `trace.enabled`
   * @return The public facing environment variable name
   */
  private static String propertyNameToEnvironmentVariableName(String setting) {
    return ENV_REPLACEMENT
        .matcher(propertyNameToSystemPropertyName(setting).toUpperCase())
        .replaceAll("_");
  }

  /**
   * Converts the property name, e.g. 'trace.config' into a public system property name, e.g.
   * `otel.trace.config`.
   *
   * @param setting The setting name, e.g. `trace.config`
   * @return The public facing system property name
   */
  private static String propertyNameToSystemPropertyName(String setting) {
    return PREFIX + setting;
  }

  /**
   * @param value to parse by tClass::valueOf
   * @param tClass should contain static parsing method "T valueOf(String)"
   * @param defaultValue
   * @param <T>
   * @return value == null || value.trim().isEmpty() ? defaultValue : tClass.valueOf(value)
   * @throws NumberFormatException
   */
  private static <T> T valueOf(String value, Class<T> tClass, T defaultValue) {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return (T)
          PUBLIC_LOOKUP
              .findStatic(tClass, "valueOf", MethodType.methodType(tClass, String.class))
              .invoke(value);
    } catch (NumberFormatException e) {
      throw e;
    } catch (NoSuchMethodException | IllegalAccessException e) {
      log.debug("Can't invoke or access 'valueOf': ", e);
      throw new NumberFormatException(e.toString());
    } catch (Throwable e) {
      log.debug("Can't parse: ", e);
      throw new NumberFormatException(e.toString());
    }
  }

  private static List<String> getPropertyListValue(
      Properties properties, String name, List<String> defaultValue) {
    String value = properties.getProperty(name);
    return value == null || value.trim().isEmpty() ? defaultValue : parseList(value);
  }

  private static Map<String, String> getPropertyMapValue(
      Properties properties, String name, Map<String, String> defaultValue) {
    String value = properties.getProperty(name);
    return value == null || value.trim().isEmpty() ? defaultValue : parseMap(value);
  }

  private static Boolean getPropertyBooleanValue(
      Properties properties, String name, Boolean defaultValue) {
    return valueOf(properties.getProperty(name), Boolean.class, defaultValue);
  }

  private static Integer getPropertyIntegerValue(
      Properties properties, String name, Integer defaultValue) {
    return valueOf(properties.getProperty(name), Integer.class, defaultValue);
  }

  private static List<String> parseList(String str) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyList();
    }

    String[] tokens = str.split(",", -1);
    // Remove whitespace from each item.
    for (int i = 0; i < tokens.length; i++) {
      tokens[i] = tokens[i].trim();
    }
    return Collections.unmodifiableList(Arrays.asList(tokens));
  }

  private static Map<String, String> parseMap(String str) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, String> result = new LinkedHashMap<>();
    for (String token : str.split(",", -1)) {
      token = token.trim();
      String[] parts = token.split("=", -1);
      if (parts.length != 2) {
        log.warn("Invalid map config part, should be formatted key1=value1,key2=value2: {}", str);
        return Collections.emptyMap();
      }
      result.put(parts[0], parts[1]);
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Loads the optional configuration properties file into the global {@link Properties} object.
   *
   * @return The {@link Properties} object. the returned instance might be empty of file does not
   *     exist or if it is in a wrong format.
   */
  private static Properties loadConfigurationFile() {
    Properties properties = new Properties();

    // Reading from system property first and from env after
    String configurationFilePath =
        System.getProperty(propertyNameToSystemPropertyName(CONFIGURATION_FILE));
    if (null == configurationFilePath) {
      configurationFilePath =
          System.getenv(propertyNameToEnvironmentVariableName(CONFIGURATION_FILE));
    }
    if (null == configurationFilePath) {
      return properties;
    }

    // Normalizing tilde (~) paths for unix systems
    configurationFilePath =
        configurationFilePath.replaceFirst("^~", System.getProperty("user.home"));

    // Configuration properties file is optional
    File configurationFile = new File(configurationFilePath);
    if (!configurationFile.exists()) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
      return properties;
    }

    try (FileReader fileReader = new FileReader(configurationFile)) {
      properties.load(fileReader);
    } catch (FileNotFoundException fnf) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
    } catch (IOException ioe) {
      log.error(
          "Configuration file '{}' cannot be accessed or correctly parsed.", configurationFilePath);
    }

    return properties;
  }

  private static String toUpper(String str) {
    return str == null ? null : str.toUpperCase(Locale.ENGLISH);
  }

  // This has to be placed after all other static fields to give them a chance to initialize
  private static final Config INSTANCE = new Config();

  public static Config get() {
    return INSTANCE;
  }

  public static Config get(Properties properties) {
    if (properties == null || properties.isEmpty()) {
      return INSTANCE;
    } else {
      return new Config(properties, INSTANCE);
    }
  }

  public String getExporterJar() {
    return exporterJar;
  }

  public String getExporter() {
    return exporter;
  }

  public List<String> getPropagators() {
    return propagators;
  }

  public boolean isTraceEnabled() {
    return traceEnabled;
  }

  public boolean isIntegrationsEnabled() {
    return integrationsEnabled;
  }

  public List<String> getExcludedClasses() {
    return excludedClasses;
  }

  public boolean isHttpServerTagQueryString() {
    return httpServerTagQueryString;
  }

  public boolean isHttpClientTagQueryString() {
    return httpClientTagQueryString;
  }

  public Integer getScopeDepthLimit() {
    return scopeDepthLimit;
  }

  public boolean isRuntimeContextFieldInjection() {
    return runtimeContextFieldInjection;
  }

  public String getTraceAnnotations() {
    return traceAnnotations;
  }

  public String getTraceMethods() {
    return traceMethods;
  }

  public String getTraceAnnotatedMethodsExclude() {
    return traceAnnotatedMethodsExclude;
  }

  public boolean isTraceExecutorsAll() {
    return traceExecutorsAll;
  }

  public List<String> getTraceExecutors() {
    return traceExecutors;
  }

  public boolean isSqlNormalizerEnabled() {
    return sqlNormalizerEnabled;
  }

  public boolean isKafkaClientPropagationEnabled() {
    return kafkaClientPropagationEnabled;
  }

  public boolean isHystrixTagsEnabled() {
    return hystrixTagsEnabled;
  }

  public Map<String, String> getEndpointPeerServiceMapping() {
    return endpointPeerServiceMapping;
  }

  @Override
  public String toString() {
    return "Config{"
        + "exporterJar='"
        + exporterJar
        + '\''
        + ", exporter='"
        + exporter
        + '\''
        + ", propagators="
        + propagators
        + ", traceEnabled="
        + traceEnabled
        + ", integrationsEnabled="
        + integrationsEnabled
        + ", excludedClasses="
        + excludedClasses
        + ", httpServerTagQueryString="
        + httpServerTagQueryString
        + ", httpClientTagQueryString="
        + httpClientTagQueryString
        + ", scopeDepthLimit="
        + scopeDepthLimit
        + ", runtimeContextFieldInjection="
        + runtimeContextFieldInjection
        + ", traceAnnotations='"
        + traceAnnotations
        + '\''
        + ", traceMethods='"
        + traceMethods
        + '\''
        + ", traceAnnotatedMethodsExclude='"
        + traceAnnotatedMethodsExclude
        + '\''
        + ", traceExecutorsAll="
        + traceExecutorsAll
        + ", traceExecutors="
        + traceExecutors
        + ", sqlNormalizerEnabled="
        + sqlNormalizerEnabled
        + ", kafkaClientPropagationEnabled="
        + kafkaClientPropagationEnabled
        + ", hystrixTagsEnabled="
        + hystrixTagsEnabled
        + ", endpointPeerServiceMapping="
        + endpointPeerServiceMapping
        + '}';
  }
}
