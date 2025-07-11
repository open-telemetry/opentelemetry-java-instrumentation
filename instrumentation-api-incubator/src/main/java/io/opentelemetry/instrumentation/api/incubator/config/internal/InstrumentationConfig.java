/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
public interface InstrumentationConfig {

  /**
   * Returns a string-valued configuration property or {@code null} if a property with name {@code
   * name} has not been configured.
   */
  @Nullable
  String getString(String name);

  /**
   * Returns a string-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured.
   */
  String getString(String name, String defaultValue);

  /**
   * Returns a boolean-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured.
   */
  boolean getBoolean(String name, boolean defaultValue);

  /**
   * Returns an integer-valued configuration property or {@code defaultValue} if a property with
   * name {@code name} has not been configured or when parsing has failed.
   */
  int getInt(String name, int defaultValue);

  /**
   * Returns a long-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed.
   */
  long getLong(String name, long defaultValue);

  /**
   * Returns a double-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed.
   */
  double getDouble(String name, double defaultValue);

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
  Duration getDuration(String name, Duration defaultValue);

  /**
   * This is the same as calling {@code getList(String, List)} with the defaultValue equal to the
   * emptyList()/
   */
  default List<String> getList(String name) {
    return getList(name, emptyList());
  }

  /**
   * Returns a list-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured. The format of the original value must be comma-separated,
   * e.g. {@code one,two,three}. The returned list is unmodifiable.
   */
  List<String> getList(String name, List<String> defaultValue);

  /**
   * Returns a map-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured or when parsing has failed. The format of the original
   * value must be comma-separated for each key, with an '=' separating the key and value, e.g.
   * {@code key=value,anotherKey=anotherValue}. The returned map is unmodifiable.
   */
  Map<String, String> getMap(String name, Map<String, String> defaultValue);

  /**
   * Returns a {@link DeclarativeConfigProperties} for the given instrumentation name, or {@code
   * null} if no declarative configuration is available for that instrumentation.
   *
   * <p>Declarative configuration is used to configure instrumentation properties in a declarative
   * way, such as through YAML or JSON files.
   *
   * @param instrumentationName the name of the instrumentation
   * @return the declarative configuration properties for the given instrumentation name, or {@code
   *     null} if not available
   */
  @Nullable
  DeclarativeConfigProperties getDeclarativeConfig(String instrumentationName);
}
