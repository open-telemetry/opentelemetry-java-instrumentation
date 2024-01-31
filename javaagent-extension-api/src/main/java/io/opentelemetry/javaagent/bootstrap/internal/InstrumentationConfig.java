/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Represents the global instrumentation configuration consisting of system properties, environment
 * variables, contents of the agent configuration file and properties defined by the {@code
 * ConfigPropertySource} SPI implementations.
 *
 * <p>In case any {@code get*()} method variant gets called for the same property more than once
 * (e.g. each time an advice class executes) it is suggested to cache the result instead of
 * repeatedly calling {@link InstrumentationConfig}. Instrumentation configuration does not change
 * during the runtime so retrieving the property once and storing its result in a static final field
 * allows JIT to do its magic and remove some code branches.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public abstract class InstrumentationConfig {

  private static final Logger logger = Logger.getLogger(InstrumentationConfig.class.getName());

  private static final InstrumentationConfig DEFAULT = new EmptyInstrumentationConfig();

  // lazy initialized, so that javaagent can set it
  private static volatile InstrumentationConfig instance = DEFAULT;

  /**
   * Sets the instrumentation configuration singleton. This method is only supposed to be called
   * once, during the agent initialization, just before {@link InstrumentationConfig#get()} is used
   * for the first time.
   *
   * <p>This method is internal and is hence not for public use. Its API is unstable and can change
   * at any time.
   */
  public static void internalInitializeConfig(InstrumentationConfig config) {
    if (instance != DEFAULT) {
      logger.warning("InstrumentationConfig#instance was already set earlier");
      return;
    }
    instance = requireNonNull(config);
  }

  /** Returns the global instrumentation configuration. */
  public static InstrumentationConfig get() {
    return instance;
  }

  /**
   * Returns a string-valued configuration property or {@code null} if a property with name {@code
   * name} has not been configured.
   */
  @Nullable
  public abstract String getString(String name);

  /**
   * Returns a string-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured.
   */
  public abstract String getString(String name, String defaultValue);

  /**
   * Returns a boolean-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured.
   */
  public abstract boolean getBoolean(String name, boolean defaultValue);

  /**
   * Returns an integer-valued configuration property or {@code defaultValue} if a property with
   * name {@code name} has not been configured or when parsing has failed.
   */
  public abstract int getInt(String name, int defaultValue);

  /**
   * Returns a long-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed.
   */
  public abstract long getLong(String name, long defaultValue);

  /**
   * Returns a double-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed.
   */
  public abstract double getDouble(String name, double defaultValue);

  /**
   * Returns a duration-valued configuration property or {@code defaultValue} if a property with
   * name {@code name} has not been configured or when parsing has failed.
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
   * <p>Examples: 10s, 20ms, 5000
   */
  public abstract Duration getDuration(String name, Duration defaultValue);

  /**
   * This is the same as calling {@code getList(String, List)} with the defaultValue equal to the
   * emptyList()/
   */
  public List<String> getList(String name) {
    return getList(name, emptyList());
  }

  /**
   * Returns a list-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured. The format of the original value must be comma-separated,
   * e.g. {@code one,two,three}. The returned list is unmodifiable.
   */
  public abstract List<String> getList(String name, List<String> defaultValue);

  /**
   * Returns a map-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed. The format of the original
   * value must be comma-separated for each key, with an '=' separating the key and value, e.g.
   * {@code key=value,anotherKey=anotherValue}. The returned map is unmodifiable.
   */
  public abstract Map<String, String> getMap(String name, Map<String, String> defaultValue);
}
