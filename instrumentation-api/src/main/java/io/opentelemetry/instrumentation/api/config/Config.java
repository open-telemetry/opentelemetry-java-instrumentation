/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoValue
public abstract class Config {
  private static final Logger log = LoggerFactory.getLogger(Config.class);
  private static final Pattern PROPERTY_NAME_REPLACEMENTS = Pattern.compile("[^a-zA-Z0-9.]");

  private static final Config DEFAULT = Config.createFromAlreadyNormalized(Collections.emptyMap());

  // INSTANCE can never be null - muzzle instantiates instrumenters when it generates
  // getMuzzleReferenceMatcher() and the InstrumentationModule constructor uses Config
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

  public static Config createFromAlreadyNormalized(Map<String, String> allProperties) {
    return new AutoValue_Config(allProperties);
  }

  // only used by tests
  static Config create(Map<String, String> allProperties) {
    Map<String, String> normalized = new HashMap<>(allProperties.size());
    allProperties.forEach((key, value) -> normalized.put(normalizePropertyName(key), value));
    return createFromAlreadyNormalized(normalized);
  }

  abstract Map<String, String> getAllProperties();

  /**
   * Returns a string property value or null if a property with name {@code name} did not exist.
   *
   * @see #getProperty(String, String)
   */
  @Nullable
  public String getProperty(String name) {
    return getProperty(name, null);
  }

  /**
   * Retrieves a property value from the agent configuration consisting of system properties,
   * environment variables and contents of the agent configuration file (see {@code
   * ConfigInitializer} and {@code ConfigBuilder}).
   *
   * <p>In case any {@code get*Property()} method variant gets called for the same property more
   * than once (e.g. each time an advice executes) it is suggested to cache the result instead of
   * repeatedly calling {@link Config}. Agent configuration does not change during the runtime so
   * retrieving the property once and storing its result in e.g. static final field allows JIT to do
   * its magic and remove some code branches.
   *
   * @return A string property value or {@code defaultValue} if a property with name {@code name}
   *     did not exist.
   */
  public String getProperty(String name, String defaultValue) {
    return getAllProperties().getOrDefault(normalizePropertyName(name), defaultValue);
  }

  /**
   * Returns a boolean property value or {@code defaultValue} if a property with name {@code name}
   * did not exist.
   *
   * @see #getProperty(String, String)
   */
  public boolean getBooleanProperty(String name, boolean defaultValue) {
    return getTypedProperty(name, Boolean::parseBoolean, defaultValue);
  }

  /**
   * Returns a list-of-strings property value or empty list if a property with name {@code name} did
   * not exist.
   *
   * @see #getProperty(String, String)
   */
  public List<String> getListProperty(String name) {
    return getListProperty(name, Collections.emptyList());
  }

  /**
   * Returns a list-of-strings property value or {@code defaultValue} if a property with name {@code
   * name} did not exist.
   *
   * @see #getProperty(String, String)
   */
  public List<String> getListProperty(String name, List<String> defaultValue) {
    return getTypedProperty(name, CollectionParsers::parseList, defaultValue);
  }

  /**
   * Returns a map-of-strings property value or empty map if a property with name {@code name} did
   * not exist.
   *
   * @see #getProperty(String, String)
   */
  public Map<String, String> getMapProperty(String name) {
    return getTypedProperty(name, CollectionParsers::parseMap, Collections.emptyMap());
  }

  private <T> T getTypedProperty(String name, Function<String, T> parser, T defaultValue) {
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

  // some instrumentation names have '-' or '_' character -- this does not work well with
  // environment variables (where we replace every non-alphanumeric character with '.'), so we're
  // replacing those with a dot
  public static String normalizePropertyName(String propertyName) {
    return PROPERTY_NAME_REPLACEMENTS.matcher(propertyName.toLowerCase()).replaceAll(".");
  }

  public boolean isInstrumentationEnabled(
      Iterable<String> instrumentationNames, boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (String name : instrumentationNames) {
      boolean configEnabled =
          getBooleanProperty("otel.instrumentation." + name + ".enabled", defaultEnabled);

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
}
