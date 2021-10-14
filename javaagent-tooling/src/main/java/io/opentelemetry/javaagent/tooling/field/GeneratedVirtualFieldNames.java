/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

final class GeneratedVirtualFieldNames {

  /**
   * Note: the value here has to be inside on of the prefixes in {@link
   * io.opentelemetry.javaagent.tooling.Constants#BOOTSTRAP_PACKAGE_PREFIXES}. This ensures that
   * 'isolating' (or 'module') classloaders like jboss and osgi see injected classes. This works
   * because we instrument those classloaders to load everything inside bootstrap packages.
   */
  static final String DYNAMIC_CLASSES_PACKAGE = "io.opentelemetry.javaagent.bootstrap.field.";

  private GeneratedVirtualFieldNames() {}

  static String getVirtualFieldImplementationClassName(String typeName, String fieldTypeName) {
    return DYNAMIC_CLASSES_PACKAGE
        + "VirtualFieldImpl$"
        + sanitizeClassName(typeName)
        + "$"
        + sanitizeClassName(fieldTypeName);
  }

  static String getFieldAccessorInterfaceName(String typeName, String fieldTypeName) {
    return DYNAMIC_CLASSES_PACKAGE
        + "VirtualFieldAccessor$"
        + sanitizeClassName(typeName)
        + "$"
        + sanitizeClassName(fieldTypeName);
  }

  static String getRealFieldName(String typeName, String fieldTypeName) {
    return "__opentelemetryVirtualField$"
        + sanitizeClassName(typeName)
        + "$"
        + sanitizeClassName(fieldTypeName);
  }

  static String getRealGetterName(String typeName, String fieldTypeName) {
    // it's important for the get and set methods to not look like normal getters/setters, otherwise
    // reflection frameworks (e.g. spring-beans) may interpret them as properties
    return "__get" + getRealFieldName(typeName, fieldTypeName);
  }

  static String getRealSetterName(String typeName, String fieldTypeName) {
    // it's important for the get and set methods to not look like normal getters/setters, otherwise
    // reflection frameworks (e.g. spring-beans) may interpret them as properties
    return "__set" + getRealFieldName(typeName, fieldTypeName);
  }

  private static String sanitizeClassName(String className) {
    className = className.replace('.', '$');
    if (className.endsWith("[]")) {
      className = className.replace('[', '_').replace(']', '_');
    }
    return className;
  }
}
