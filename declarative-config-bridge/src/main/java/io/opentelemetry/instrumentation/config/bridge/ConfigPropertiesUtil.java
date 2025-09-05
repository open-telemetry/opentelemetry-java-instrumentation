/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

public final class ConfigPropertiesUtil {
  private ConfigPropertiesUtil() {}

  public static String propertyYamlPath(String propertyName) {
    return yamlPath(propertyName);
  }

  static String yamlPath(String property) {
    String[] segments = DeclarativeConfigPropertiesBridge.getSegments(property);
    if (segments.length == 0) {
      throw new IllegalArgumentException("Invalid property: " + property);
    }

    return "'instrumentation/development' / 'java' / '" + String.join("' / '", segments) + "'";
  }
}
