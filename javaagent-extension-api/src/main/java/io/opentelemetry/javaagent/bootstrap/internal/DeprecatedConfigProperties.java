/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Collections.emptyList;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("unused")
public final class DeprecatedConfigProperties {

  private static final Logger logger = Logger.getLogger(DeprecatedConfigProperties.class.getName());

  public static boolean getBoolean(
      InstrumentationConfig config,
      String deprecatedPropertyName,
      String newPropertyName,
      boolean defaultValue) {
    warnIfUsed(config, deprecatedPropertyName, newPropertyName);
    boolean value = config.getBoolean(deprecatedPropertyName, defaultValue);
    return config.getBoolean(newPropertyName, value);
  }

  public static List<String> getList(
      InstrumentationConfig config, String deprecatedPropertyName, String newPropertyName) {
    warnIfUsed(config, deprecatedPropertyName, newPropertyName);
    List<String> value = config.getList(deprecatedPropertyName, emptyList());
    return config.getList(newPropertyName, value);
  }

  private static void warnIfUsed(
      InstrumentationConfig config, String deprecatedPropertyName, String newPropertyName) {
    if (config.getString(deprecatedPropertyName) != null) {
      logger.log(
          WARNING,
          "Deprecated property \"{0}\" was used; use the \"{1}\" property instead",
          new Object[] {deprecatedPropertyName, newPropertyName});
    }
  }

  private DeprecatedConfigProperties() {}
}
