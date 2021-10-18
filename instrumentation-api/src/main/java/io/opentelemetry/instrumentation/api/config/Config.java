/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the global agent configuration consisting of system properties, environment variables,
 * contents of the agent configuration file and properties defined by the {@code
 * ConfigPropertySource} SPI (see {@code ConfigInitializer} and {@link ConfigBuilder}).
 *
 * <p>In case any {@code get*()} method variant gets called for the same property more than once
 * (e.g. each time an advice class executes) it is suggested to cache the result instead of
 * repeatedly calling {@link Config}. Agent configuration does not change during the runtime so
 * retrieving the property once and storing its result in e.g. static final field allows JIT to do
 * its magic and remove some code branches.
 */
@AutoValue
public abstract class Config {
  private static final Logger logger = LoggerFactory.getLogger(Config.class);

  // lazy initialized, so that javaagent can set it, and library instrumentation can fall back and
  // read system properties
  @Nullable private static volatile Config instance = null;

  /** Start building a new {@link Config} instance. */
  public static ConfigBuilder builder() {
    return new ConfigBuilder();
  }

  static Config create(Map<String, String> allProperties) {
    return new AutoValue_Config(allProperties);
  }

  // package protected constructor to make extending this class impossible
  Config() {}

  /**
   * Sets the agent configuration singleton. This method is only supposed to be called once, from
   * the agent classloader just before the first instrumentation is loaded (and before {@link
   * Config#get()} is used for the first time).
   */
  public static void internalInitializeConfig(Config config) {
    if (instance != null) {
      logger.warn("Config#INSTANCE was already set earlier");
      return;
    }
    instance = requireNonNull(config);
  }

  /** Returns the global agent configuration. */
  public static Config get() {
    if (instance == null) {
      // this should only happen in library instrumentation
      //
      // no need to synchronize because worst case is creating instance more than once
      instance = builder().readEnvironmentVariables().readSystemProperties().build();
    }
    return instance;
  }

  /** Returns all properties stored in this instance. The returned map is unmodifiable. */
  public abstract Map<String, String> getAllProperties();

  /**
   * Returns a string-valued configuration property or {@code null} if a property with name {@code
   * name} has not been configured.
   */
  @Nullable
  public String getString(String name) {
    return getRawProperty(name, null);
  }

  /**
   * Returns a string-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured.
   */
  public String getString(String name, String defaultValue) {
    return getRawProperty(name, defaultValue);
  }

  /**
   * Returns a boolean-valued configuration property or {@code null} if a property with name {@code
   * name} has not been configured.
   */
  @Nullable
  public Boolean getBoolean(String name) {
    return getTypedProperty(name, ConfigValueParsers::parseBoolean);
  }

  /**
   * Returns a boolean-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured.
   */
  public boolean getBoolean(String name, boolean defaultValue) {
    return safeGetTypedProperty(name, ConfigValueParsers::parseBoolean, defaultValue);
  }

  /**
   * Returns a integer-valued configuration property or {@code null} if a property with name {@code
   * name} has not been configured.
   *
   * @throws ConfigParsingException if the property is not a valid integer.
   */
  @Nullable
  public Integer getInt(String name) {
    return getTypedProperty(name, ConfigValueParsers::parseInt);
  }

  /**
   * Returns a integer-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed. This is the safe variant of
   * {@link #getInt(String)}.
   */
  public int getInt(String name, int defaultValue) {
    return safeGetTypedProperty(name, ConfigValueParsers::parseInt, defaultValue);
  }

  /**
   * Returns a long-valued configuration property or {@code null} if a property with name {@code
   * name} has not been configured.
   *
   * @throws ConfigParsingException if the property is not a valid long.
   */
  @Nullable
  public Long getLong(String name) {
    return getTypedProperty(name, ConfigValueParsers::parseLong);
  }

  /**
   * Returns a long-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed. This is the safe variant of
   * {@link #getLong(String)}.
   */
  public long getLong(String name, long defaultValue) {
    return safeGetTypedProperty(name, ConfigValueParsers::parseLong, defaultValue);
  }

  /**
   * Returns a double-valued configuration property or {@code null} if a property with name {@code
   * name} has not been configured.
   *
   * @throws ConfigParsingException if the property is not a valid long.
   */
  @Nullable
  public Double getDouble(String name) {
    return getTypedProperty(name, ConfigValueParsers::parseDouble);
  }

  /**
   * Returns a double-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed. This is the safe variant of
   * {@link #getDouble(String)}.
   */
  public double getDouble(String name, double defaultValue) {
    return safeGetTypedProperty(name, ConfigValueParsers::parseDouble, defaultValue);
  }

  /**
   * Returns a duration-valued configuration property or {@code null} if a property with name {@code
   * name} has not been configured.
   *
   * <p>Durations can be of the form "{number}{unit}", where unit is one of:
   *
   * <ul>
   *   <li>ms
   *   <li>s
   *   <li>m
   *   <li>h
   *   <li>d
   * </ul>
   *
   * <p>If no unit is specified, milliseconds is the assumed duration unit.
   *
   * @throws ConfigParsingException if the property is not a valid long.
   */
  @Nullable
  public Duration getDuration(String name) {
    return getTypedProperty(name, ConfigValueParsers::parseDuration);
  }

  /**
   * Returns a duration-valued configuration property or {@code defaultValue} if a property with
   * name {@code name} has not been configured or when parsing has failed. This is the safe variant
   * of {@link #getDuration(String)}.
   *
   * <p>Durations can be of the form "{number}{unit}", where unit is one of:
   *
   * <ul>
   *   <li>ms
   *   <li>s
   *   <li>m
   *   <li>h
   *   <li>d
   * </ul>
   *
   * <p>If no unit is specified, milliseconds is the assumed duration unit.
   */
  public Duration getDuration(String name, Duration defaultValue) {
    return safeGetTypedProperty(name, ConfigValueParsers::parseDuration, defaultValue);
  }

  /**
   * Returns a list-valued configuration property or an empty list if a property with name {@code
   * name} has not been configured. The format of the original value must be comma-separated, e.g.
   * {@code one,two,three}.
   */
  public List<String> getList(String name) {
    List<String> list = getTypedProperty(name, ConfigValueParsers::parseList);
    return list == null ? emptyList() : list;
  }

  /**
   * Returns a list-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured. The format of the original value must be comma-separated,
   * e.g. {@code one,two,three}.
   */
  public List<String> getList(String name, List<String> defaultValue) {
    return safeGetTypedProperty(name, ConfigValueParsers::parseList, defaultValue);
  }

  /**
   * Returns a map-valued configuration property or an empty map if a property with name {@code
   * name} has not been configured. The format of the original value must be comma-separated for
   * each key, with an '=' separating the key and value, e.g. {@code
   * key=value,anotherKey=anotherValue}.
   *
   * @throws ConfigParsingException if the property is not a valid long.
   */
  public Map<String, String> getMap(String name) {
    Map<String, String> map = getTypedProperty(name, ConfigValueParsers::parseMap);
    return map == null ? emptyMap() : map;
  }

  /**
   * Returns a map-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed. This is the safe variant of
   * {@link #getMap(String)}. The format of the original value must be comma-separated for each key,
   * with an '=' separating the key and value, e.g. {@code key=value,anotherKey=anotherValue}.
   */
  public Map<String, String> getMap(String name, Map<String, String> defaultValue) {
    return safeGetTypedProperty(name, ConfigValueParsers::parseMap, defaultValue);
  }

  private <T> T safeGetTypedProperty(String name, ConfigValueParser<T> parser, T defaultValue) {
    try {
      T value = getTypedProperty(name, parser);
      return value == null ? defaultValue : value;
    } catch (RuntimeException t) {
      logger.debug("Error occurred during parsing: {}", t.getMessage(), t);
      return defaultValue;
    }
  }

  @Nullable
  private <T> T getTypedProperty(String name, ConfigValueParser<T> parser) {
    String value = getRawProperty(name, null);
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return parser.parse(name, value);
  }

  private String getRawProperty(String name, String defaultValue) {
    return getAllProperties().getOrDefault(NamingConvention.DOT.normalize(name), defaultValue);
  }

  public boolean isInstrumentationEnabled(
      Iterable<String> instrumentationNames, boolean defaultEnabled) {
    return isInstrumentationPropertyEnabled(instrumentationNames, "enabled", defaultEnabled);
  }

  public boolean isInstrumentationPropertyEnabled(
      Iterable<String> instrumentationNames, String suffix, boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (String name : instrumentationNames) {
      String propertyName = "otel.instrumentation." + name + '.' + suffix;
      boolean enabled = getBoolean(propertyName, defaultEnabled);

      if (defaultEnabled) {
        anyEnabled &= enabled;
      } else {
        anyEnabled |= enabled;
      }
    }
    return anyEnabled;
  }

  public boolean isAgentDebugEnabled() {
    return getBoolean("otel.javaagent.debug", false);
  }

  /**
   * Converts this config instance to Java {@link Properties}.
   *
   * @deprecated Use {@link #getAllProperties()} instead.
   */
  @Deprecated
  public Properties asJavaProperties() {
    Properties properties = new Properties();
    properties.putAll(getAllProperties());
    return properties;
  }
}
