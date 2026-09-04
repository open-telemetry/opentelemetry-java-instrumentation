/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessorMarker;

final class GeneratedVirtualFieldNames {

  /**
   * Note: the value here has to be inside on of the prefixes in {@link
   * io.opentelemetry.javaagent.tooling.Constants#BOOTSTRAP_PACKAGE_PREFIXES}. This ensures that
   * 'isolating' (or 'module') classloaders like jboss and osgi see injected classes. This works
   * because we instrument those classloaders to load everything inside bootstrap packages.
   */
  static final String DYNAMIC_CLASSES_PACKAGE =
      VirtualFieldAccessorMarker.class.getPackage().getName() + ".";

  private GeneratedVirtualFieldNames() {}

  public static boolean isVirtualFieldInterfaceName(String className) {
    return className.startsWith(DYNAMIC_CLASSES_PACKAGE + "VirtualFieldAccessor$");
  }

  static String getVirtualFieldImplementationClassName(
      String fieldName, String typeName, String fieldTypeName) {
    return DYNAMIC_CLASSES_PACKAGE
        + "VirtualFieldImpl$"
        + (!fieldName.isEmpty() ? fieldName + "$" : "")
        + sanitizeClassName(typeName)
        + "$"
        + sanitizeClassName(fieldTypeName);
  }

  static String getFieldAccessorInterfaceName(
      String fieldName, String typeName, String fieldTypeName) {
    return DYNAMIC_CLASSES_PACKAGE
        + "VirtualFieldAccessor$"
        + (!fieldName.isEmpty() ? fieldName + "$" : "")
        + sanitizeClassName(typeName)
        + "$"
        + sanitizeClassName(fieldTypeName);
  }

  static String getRealFieldName(String fieldName, String typeName, String fieldTypeName) {
    return "__opentelemetryVirtualField$"
        + (!fieldName.isEmpty() ? fieldName + "$" : "")
        + sanitizeClassName(typeName)
        + "$"
        + sanitizeClassName(fieldTypeName);
  }

  static String getRealGetterName(String fieldName, String typeName, String fieldTypeName) {
    return "__get" + getRealFieldName(fieldName, typeName, fieldTypeName);
  }

  static String getRealSetterName(String fieldName, String typeName, String fieldTypeName) {
    return "__set" + getRealFieldName(fieldName, typeName, fieldTypeName);
  }

  private static String sanitizeClassName(String className) {
    className = className.replace('.', '$');
    if (className.endsWith("[]")) {
      className = className.replace('[', '_').replace(']', '_');
    }
    return className;
  }
}
