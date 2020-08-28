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

package io.opentelemetry.javaagent.tooling.exporter;

/**
 * An interface used to provide configuration information to {@link SpanExporterFactory}
 * implementations. Callers (such as the Java Auto Instrumenter) typically provide an implementation
 * mapping directly to their native configuration framework.
 *
 * <p>This interface is intentionally kept very simple since the underlying implementations may only
 * have access to very basic configuration mechanisms such as system properties and environment
 * variables.
 */
public interface ExporterConfig {
  /**
   * Returns the string configuration property corresponding to a key. If the underlying
   * implementation cannot find a property for the key, {@code defaultValue} is returned.
   *
   * @param key The config key
   * @param defaultValue The value to use if no configuration property couldn't be found
   * @return The value of the configuration parameter
   */
  String getString(String key, String defaultValue);

  /**
   * Returns the {@code int} configuration property corresponding to a key. If the underlying
   * implementation cannot find a property for the key, {@code defaultValue} is returned.
   *
   * @param key The config key
   * @param defaultValue The value to use if no configuration property couldn't be found
   * @return The value of the configuration parameter
   */
  int getInt(String key, int defaultValue);

  /**
   * Returns the {@code long} configuration property corresponding to a key. If the underlying
   * implementation cannot find a property for the key, {@code defaultValue} is returned.
   *
   * @param key The config key
   * @param defaultValue The value to use if no configuration property couldn't be found
   * @return The value of the configuration parameter
   */
  long getLong(String key, long defaultValue);

  /**
   * Returns the {@code boolean} configuration property corresponding to a key. If the underlying
   * implementation cannot find a property for the key, {@code defaultValue} is returned.
   *
   * @param key The config key
   * @param defaultValue The value to use if no configuration property couldn't be found
   * @return The value of the configuration parameter
   */
  boolean getBoolean(String key, boolean defaultValue);

  /**
   * Returns the {@code double} configuration property corresponding to a key. If the underlying
   * implementation cannot find a property for the key, {@code defaultValue} is returned.
   *
   * @param key The config key
   * @param defaultValue The value to use if no configuration property couldn't be found
   * @return The value of the configuration parameter
   */
  double getDouble(String key, double defaultValue);
}
