/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.ConcurrentModificationException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;

/**
 * Configuration utilities.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 *
 * <p>Copied from <a
 * href="https://github.com/open-telemetry/opentelemetry-java/blob/main/api/all/src/main/java/io/opentelemetry/api/internal/ConfigUtil.java">SDK</a>
 * because some tests target an SDK version where this class does not exist.
 */
final class ConfigUtil {

  private ConfigUtil() {}

  /**
   * Returns a copy of system properties which is safe to iterate over.
   *
   * <p>In java 8 and android environments, iterating through system properties may trigger {@link
   * ConcurrentModificationException}. This method ensures callers can iterate safely without risk
   * of exception. See https://github.com/open-telemetry/opentelemetry-java/issues/6732 for details.
   */
  public static Properties safeSystemProperties() {
    return (Properties) System.getProperties().clone();
  }

  /**
   * Return the system property or environment variable for the {@code key}.
   *
   * <p>Normalize the {@code key} using {@link #normalizePropertyKey(String)}. Match to system
   * property keys also normalized with {@link #normalizePropertyKey(String)}. Match to environment
   * variable keys normalized with {@link #normalizeEnvironmentVariableKey(String)}. System
   * properties take priority over environment variables.
   *
   * @param key the property key
   * @return the system property if not null, or the environment variable if not null, or {@code
   *     null}
   */
  @Nullable
  public static String getString(String key) {
    String normalizedKey = normalizePropertyKey(key);

    for (Map.Entry<Object, Object> entry : safeSystemProperties().entrySet()) {
      if (normalizedKey.equals(normalizePropertyKey(entry.getKey().toString()))) {
        return entry.getValue().toString();
      }
    }

    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      if (normalizedKey.equals(normalizeEnvironmentVariableKey(entry.getKey()))) {
        return entry.getValue();
      }
    }

    return null;
  }

  /**
   * Normalize an environment variable key by converting to lower case and replacing "_" with ".".
   */
  public static String normalizeEnvironmentVariableKey(String key) {
    return key.toLowerCase(Locale.ROOT).replace("_", ".");
  }

  /** Normalize a property key by converting to lower case and replacing "-" with ".". */
  public static String normalizePropertyKey(String key) {
    return key.toLowerCase(Locale.ROOT).replace("-", ".");
  }
}
