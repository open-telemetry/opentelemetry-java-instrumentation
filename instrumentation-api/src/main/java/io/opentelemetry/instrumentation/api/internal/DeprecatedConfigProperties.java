/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Collections.emptyList;
import static java.util.logging.Level.WARNING;

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
      String deprecatedPropertyName, String newPropertyName, boolean defaultValue) {

    warnIfUsed(deprecatedPropertyName, newPropertyName);

    boolean value = ConfigPropertiesUtil.getBoolean(deprecatedPropertyName, defaultValue);
    return ConfigPropertiesUtil.getBoolean(newPropertyName, value);
  }

  public static List<String> getList(String deprecatedPropertyName, String newPropertyName) {

    warnIfUsed(deprecatedPropertyName, newPropertyName);

    List<String> value = ConfigPropertiesUtil.getList(deprecatedPropertyName, emptyList());
    return ConfigPropertiesUtil.getList(newPropertyName, value);
  }

  private static void warnIfUsed(String deprecatedPropertyName, String newPropertyName) {
    if (ConfigPropertiesUtil.getString(deprecatedPropertyName) != null) {
      logger.log(
          WARNING,
          "Deprecated property \"{0}\" was used; use the \"{1}\" property instead",
          new Object[] {deprecatedPropertyName, newPropertyName});
    }
  }

  private DeprecatedConfigProperties() {}
}
