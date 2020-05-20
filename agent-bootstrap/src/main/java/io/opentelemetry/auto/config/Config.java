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
package io.opentelemetry.auto.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Config reads values with the following priority: 1) system properties, 2) environment variables,
 * 3) optional configuration file. It also includes default values to ensure a valid config.
 *
 * <p>
 *
 * <p>System properties are {@link Config#PREFIX}'ed. Environment variables are the same as the
 * system property, but uppercased and '.' is replaced with '_'.
 */
@Slf4j
@ToString(includeFieldNames = true)
public class Config {
  private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();

  /** Config keys below */
  private static final String PREFIX = "ota.";

  private static final Pattern ENV_REPLACEMENT = Pattern.compile("[^a-zA-Z0-9_]");

  public static final String EXPORTER_JAR = "exporter.jar";
  public static final String PROPAGATORS = "propagators";
  public static final String CONFIGURATION_FILE = "trace.config";
  public static final String TRACE_ENABLED = "trace.enabled";
  public static final String INTEGRATIONS_ENABLED = "integrations.enabled";
  public static final String TRACE_ANNOTATIONS = "trace.annotations";
  public static final String TRACE_EXECUTORS_ALL = "trace.executors.all";
  public static final String TRACE_EXECUTORS = "trace.executors";
  public static final String TRACE_METHODS = "trace.methods";
  public static final String TRACE_METHODS_EXCLUDE = "trace.methods.exclude";
  public static final String TRACE_CLASSES_EXCLUDE = "trace.classes.exclude";
  public static final String HTTP_SERVER_ERROR_STATUSES = "http.server.error.statuses";
  public static final String HTTP_CLIENT_ERROR_STATUSES = "http.client.error.statuses";
  public static final String HTTP_SERVER_TAG_QUERY_STRING = "http.server.tag.query-string";
  public static final String HTTP_CLIENT_TAG_QUERY_STRING = "http.client.tag.query-string";
  public static final String SCOPE_DEPTH_LIMIT = "trace.scope.depth.limit";
  public static final String RUNTIME_CONTEXT_FIELD_INJECTION =
      "trace.runtime.context.field.injection";

  public static final String LOG_INJECTION_ENABLED = "log.injection.enabled";
  public static final String EXPERIMENTAL_LOG_CAPTURE_THRESHOLD =
      "experimental.log.capture.threshold";

  private static final boolean DEFAULT_TRACE_ENABLED = true;
  public static final boolean DEFAULT_INTEGRATIONS_ENABLED = true;

  private static final boolean DEFAULT_RUNTIME_CONTEXT_FIELD_INJECTION = true;

  private static final Set<Integer> DEFAULT_HTTP_SERVER_ERROR_STATUSES =
      parseIntegerRangeSet("500-599", "default");
  private static final Set<Integer> DEFAULT_HTTP_CLIENT_ERROR_STATUSES =
      parseIntegerRangeSet("400-599", "default");
  private static final boolean DEFAULT_HTTP_SERVER_TAG_QUERY_STRING = false;
  private static final boolean DEFAULT_HTTP_CLIENT_TAG_QUERY_STRING = false;
  private static final int DEFAULT_SCOPE_DEPTH_LIMIT = 100;

  public static final boolean DEFAULT_LOG_INJECTION_ENABLED = false;
  public static final String DEFAULT_EXPERIMENTAL_LOG_CAPTURE_THRESHOLD = null;

  private static final String DEFAULT_TRACE_ANNOTATIONS = null;
  private static final boolean DEFAULT_TRACE_EXECUTORS_ALL = false;
  private static final String DEFAULT_TRACE_EXECUTORS = "";
  private static final String DEFAULT_TRACE_METHODS = null;
  private static final String DEFAULT_TRACE_METHODS_EXCLUDE = null;

  public static final String SQL_NORMALIZER_ENABLED = "sql.normalizer.enabled";
  public static final boolean DEFAULT_SQL_NORMALIZER_ENABLED = true;

  @Getter private final String exporterJar;
  @Getter private final List<String> propagators;
  @Getter private final boolean traceEnabled;
  @Getter private final boolean integrationsEnabled;
  @Getter private final List<String> excludedClasses;
  @Getter private final Set<Integer> httpServerErrorStatuses;
  @Getter private final Set<Integer> httpClientErrorStatuses;
  @Getter private final boolean httpServerTagQueryString;
  @Getter private final boolean httpClientTagQueryString;
  @Getter private final Integer scopeDepthLimit;
  @Getter private final boolean runtimeContextFieldInjection;

  @Getter private final boolean logInjectionEnabled;

  // mapping of threshold values to different logging frameworks:
  //
  // | Threshold    | JUL     | Logback | Log4j  |
  // |--------------|---------|---------|--------|
  // | OFF          | OFF     | OFF     | OFF    |
  // | FATAL        | SEVERE  | ERROR   | FATAL  |
  // | ERROR/SEVERE | SEVERE  | ERROR   | ERROR  |
  // | WARN/WARNING | WARNING | WARN    | WARN   |
  // | INFO         | INFO    | INFO    | INFO   |
  // | CONFIG       | CONFIG  | DEBUG   | DEBUG  |
  // | DEBUG/FINE   | FINE    | DEBUG   | DEBUG  |
  // | FINER        | FINER   | DEBUG   | DEBUG  |
  // | TRACE/FINEST | FINEST  | TRACE   | TRACE  |
  // | ALL          | ALL     | ALL     | ALL    |
  @Getter private final String experimentalLogCaptureThreshold;

  @Getter private final String traceAnnotations;

  @Getter private final String traceMethods;
  @Getter private final String traceMethodsExclude;

  @Getter private final boolean traceExecutorsAll;
  @Getter private final List<String> traceExecutors;

  @Getter private final boolean sqlNormalizerEnabled;

  // Values from an optionally provided properties file
  private static Properties propertiesFromConfigFile;

  // Read order: System Properties -> Env Variables, [-> properties file], [-> default value]
  // Visible for testing
  Config() {
    propertiesFromConfigFile = loadConfigurationFile();

    propagators = getListSettingFromEnvironment(PROPAGATORS, null);
    exporterJar = getSettingFromEnvironment(EXPORTER_JAR, null);
    traceEnabled = getBooleanSettingFromEnvironment(TRACE_ENABLED, DEFAULT_TRACE_ENABLED);
    integrationsEnabled =
        getBooleanSettingFromEnvironment(INTEGRATIONS_ENABLED, DEFAULT_INTEGRATIONS_ENABLED);

    excludedClasses = getListSettingFromEnvironment(TRACE_CLASSES_EXCLUDE, null);

    httpServerErrorStatuses =
        getIntegerRangeSettingFromEnvironment(
            HTTP_SERVER_ERROR_STATUSES, DEFAULT_HTTP_SERVER_ERROR_STATUSES);

    httpClientErrorStatuses =
        getIntegerRangeSettingFromEnvironment(
            HTTP_CLIENT_ERROR_STATUSES, DEFAULT_HTTP_CLIENT_ERROR_STATUSES);

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

    logInjectionEnabled =
        getBooleanSettingFromEnvironment(LOG_INJECTION_ENABLED, DEFAULT_LOG_INJECTION_ENABLED);

    experimentalLogCaptureThreshold =
        toUpper(
            getSettingFromEnvironment(
                EXPERIMENTAL_LOG_CAPTURE_THRESHOLD, DEFAULT_EXPERIMENTAL_LOG_CAPTURE_THRESHOLD));

    traceAnnotations = getSettingFromEnvironment(TRACE_ANNOTATIONS, DEFAULT_TRACE_ANNOTATIONS);

    traceMethods = getSettingFromEnvironment(TRACE_METHODS, DEFAULT_TRACE_METHODS);
    traceMethodsExclude =
        getSettingFromEnvironment(TRACE_METHODS_EXCLUDE, DEFAULT_TRACE_METHODS_EXCLUDE);

    traceExecutorsAll =
        getBooleanSettingFromEnvironment(TRACE_EXECUTORS_ALL, DEFAULT_TRACE_EXECUTORS_ALL);

    traceExecutors = getListSettingFromEnvironment(TRACE_EXECUTORS, DEFAULT_TRACE_EXECUTORS);

    sqlNormalizerEnabled =
        getBooleanSettingFromEnvironment(SQL_NORMALIZER_ENABLED, DEFAULT_SQL_NORMALIZER_ENABLED);

    log.debug("New instance: {}", this);
  }

  // Read order: Properties -> Parent
  private Config(final Properties properties, final Config parent) {
    exporterJar = properties.getProperty(EXPORTER_JAR, parent.exporterJar);

    propagators = getPropertyListValue(properties, PROPAGATORS, parent.propagators);

    traceEnabled = getPropertyBooleanValue(properties, TRACE_ENABLED, parent.traceEnabled);
    integrationsEnabled =
        getPropertyBooleanValue(properties, INTEGRATIONS_ENABLED, parent.integrationsEnabled);

    excludedClasses =
        getPropertyListValue(properties, TRACE_CLASSES_EXCLUDE, parent.excludedClasses);

    httpServerErrorStatuses =
        getPropertyIntegerRangeValue(
            properties, HTTP_SERVER_ERROR_STATUSES, parent.httpServerErrorStatuses);

    httpClientErrorStatuses =
        getPropertyIntegerRangeValue(
            properties, HTTP_CLIENT_ERROR_STATUSES, parent.httpClientErrorStatuses);

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

    logInjectionEnabled =
        getPropertyBooleanValue(properties, LOG_INJECTION_ENABLED, parent.logInjectionEnabled);

    experimentalLogCaptureThreshold =
        toUpper(
            properties.getProperty(
                EXPERIMENTAL_LOG_CAPTURE_THRESHOLD, parent.experimentalLogCaptureThreshold));

    traceAnnotations = properties.getProperty(TRACE_ANNOTATIONS, parent.traceAnnotations);

    traceMethods = properties.getProperty(TRACE_METHODS, parent.traceMethods);
    traceMethodsExclude = properties.getProperty(TRACE_METHODS_EXCLUDE, parent.traceMethodsExclude);

    traceExecutorsAll =
        getPropertyBooleanValue(properties, TRACE_EXECUTORS_ALL, parent.traceExecutorsAll);
    traceExecutors = getPropertyListValue(properties, TRACE_EXECUTORS, parent.traceExecutors);

    sqlNormalizerEnabled =
        getPropertyBooleanValue(properties, SQL_NORMALIZER_ENABLED, DEFAULT_SQL_NORMALIZER_ENABLED);

    log.debug("New instance: {}", this);
  }

  public boolean isIntegrationEnabled(
      final SortedSet<String> integrationNames, final boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (final String name : integrationNames) {
      final boolean configEnabled =
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
   * Helper method that takes the name, adds a "ota." prefix then checks for System Properties of
   * that name. If none found, the name is converted to an Environment Variable and used to check
   * the env. If none of the above returns a value, then an optional properties file if checked. If
   * setting is not configured in either location, <code>defaultValue</code> is returned.
   *
   * @param name
   * @param defaultValue
   * @return
   * @deprecated This method should only be used internally. Use the explicit getter instead.
   */
  public static String getSettingFromEnvironment(final String name, final String defaultValue) {
    String value;
    final String systemPropertyName = propertyNameToSystemPropertyName(name);

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
  @NonNull
  private static List<String> getListSettingFromEnvironment(
      final String name, final String defaultValue) {
    return parseList(getSettingFromEnvironment(name, defaultValue));
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a Boolean.
   */
  private static Boolean getBooleanSettingFromEnvironment(
      final String name, final Boolean defaultValue) {
    return getSettingFromEnvironmentWithLog(name, Boolean.class, defaultValue);
  }

  /**
   * Calls {@link #getSettingFromEnvironment(String, String)} and converts the result to a Integer.
   */
  private static Integer getIntegerSettingFromEnvironment(
      final String name, final Integer defaultValue) {
    return getSettingFromEnvironmentWithLog(name, Integer.class, defaultValue);
  }

  private static <T> T getSettingFromEnvironmentWithLog(
      final String name, final Class<T> tClass, final T defaultValue) {
    try {
      return valueOf(getSettingFromEnvironment(name, null), tClass, defaultValue);
    } catch (final NumberFormatException e) {
      log.warn("Invalid configuration for " + name, e);
      return defaultValue;
    }
  }

  private static Set<Integer> getIntegerRangeSettingFromEnvironment(
      final String name, final Set<Integer> defaultValue) {
    final String value = getSettingFromEnvironment(name, null);
    try {
      return value == null ? defaultValue : parseIntegerRangeSet(value, name);
    } catch (final NumberFormatException e) {
      log.warn("Invalid configuration for " + name, e);
      return defaultValue;
    }
  }

  /**
   * Converts the property name, e.g. 'trace.enabled' into a public environment variable name, e.g.
   * `OTA_TRACE_ENABLED`.
   *
   * @param setting The setting name, e.g. `trace.enabled`
   * @return The public facing environment variable name
   */
  @NonNull
  private static String propertyNameToEnvironmentVariableName(final String setting) {
    return ENV_REPLACEMENT
        .matcher(propertyNameToSystemPropertyName(setting).toUpperCase())
        .replaceAll("_");
  }

  /**
   * Converts the property name, e.g. 'trace.config' into a public system property name, e.g.
   * `ota.trace.config`.
   *
   * @param setting The setting name, e.g. `trace.config`
   * @return The public facing system property name
   */
  @NonNull
  private static String propertyNameToSystemPropertyName(final String setting) {
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
  private static <T> T valueOf(
      final String value, @NonNull final Class<T> tClass, final T defaultValue) {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return (T)
          PUBLIC_LOOKUP
              .findStatic(tClass, "valueOf", MethodType.methodType(tClass, String.class))
              .invoke(value);
    } catch (final NumberFormatException e) {
      throw e;
    } catch (final NoSuchMethodException | IllegalAccessException e) {
      log.debug("Can't invoke or access 'valueOf': ", e);
      throw new NumberFormatException(e.toString());
    } catch (final Throwable e) {
      log.debug("Can't parse: ", e);
      throw new NumberFormatException(e.toString());
    }
  }

  private static List<String> getPropertyListValue(
      final Properties properties, final String name, final List<String> defaultValue) {
    final String value = properties.getProperty(name);
    return value == null || value.trim().isEmpty() ? defaultValue : parseList(value);
  }

  private static Boolean getPropertyBooleanValue(
      final Properties properties, final String name, final Boolean defaultValue) {
    return valueOf(properties.getProperty(name), Boolean.class, defaultValue);
  }

  private static Integer getPropertyIntegerValue(
      final Properties properties, final String name, final Integer defaultValue) {
    return valueOf(properties.getProperty(name), Integer.class, defaultValue);
  }

  private static Set<Integer> getPropertyIntegerRangeValue(
      final Properties properties, final String name, final Set<Integer> defaultValue) {
    final String value = properties.getProperty(name);
    try {
      return value == null ? defaultValue : parseIntegerRangeSet(value, name);
    } catch (final NumberFormatException e) {
      log.warn("Invalid configuration for " + name, e);
      return defaultValue;
    }
  }

  @NonNull
  private static Set<Integer> parseIntegerRangeSet(@NonNull String str, final String settingName)
      throws NumberFormatException {
    str = str.replaceAll("\\s", "");
    if (!str.matches("\\d{3}(?:-\\d{3})?(?:,\\d{3}(?:-\\d{3})?)*")) {
      log.warn(
          "Invalid config for {}: '{}'. Must be formatted like '400-403,405,410-499'.",
          settingName,
          str);
      throw new NumberFormatException();
    }

    final String[] tokens = str.split(",", -1);
    final Set<Integer> set = new HashSet<>();

    for (final String token : tokens) {
      final String[] range = token.split("-", -1);
      if (range.length == 1) {
        set.add(Integer.parseInt(range[0]));
      } else if (range.length == 2) {
        final int left = Integer.parseInt(range[0]);
        final int right = Integer.parseInt(range[1]);
        final int min = Math.min(left, right);
        final int max = Math.max(left, right);
        for (int i = min; i <= max; i++) {
          set.add(i);
        }
      }
    }
    return Collections.unmodifiableSet(set);
  }

  @NonNull
  private static List<String> parseList(final String str) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyList();
    }

    final String[] tokens = str.split(",", -1);
    // Remove whitespace from each item.
    for (int i = 0; i < tokens.length; i++) {
      tokens[i] = tokens[i].trim();
    }
    return Collections.unmodifiableList(Arrays.asList(tokens));
  }

  /**
   * Loads the optional configuration properties file into the global {@link Properties} object.
   *
   * @return The {@link Properties} object. the returned instance might be empty of file does not
   *     exist or if it is in a wrong format.
   */
  private static Properties loadConfigurationFile() {
    final Properties properties = new Properties();

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
    final File configurationFile = new File(configurationFilePath);
    if (!configurationFile.exists()) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
      return properties;
    }

    try (final FileReader fileReader = new FileReader(configurationFile)) {
      properties.load(fileReader);
    } catch (final FileNotFoundException fnf) {
      log.error("Configuration file '{}' not found.", configurationFilePath);
    } catch (final IOException ioe) {
      log.error(
          "Configuration file '{}' cannot be accessed or correctly parsed.", configurationFilePath);
    }

    return properties;
  }

  private static String toUpper(final String str) {
    return str == null ? null : str.toUpperCase(Locale.ENGLISH);
  }

  // This has to be placed after all other static fields to give them a chance to initialize
  private static final Config INSTANCE = new Config();

  public static Config get() {
    return INSTANCE;
  }

  public static Config get(final Properties properties) {
    if (properties == null || properties.isEmpty()) {
      return INSTANCE;
    } else {
      return new Config(properties, INSTANCE);
    }
  }
}
