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
      String typeName, String fieldTypeName, String fieldName) {
    return DYNAMIC_CLASSES_PACKAGE
        + "VirtualFieldImpl$"
        + (!fieldName.isEmpty() ? fieldName + "$" : "")
        + sanitizeClassName(typeName)
        + "$"
        + sanitizeClassName(fieldTypeName);
  }

  static String getFieldAccessorInterfaceName(
      String typeName, String fieldTypeName, String fieldName) {
    return DYNAMIC_CLASSES_PACKAGE
        + "VirtualFieldAccessor$"
        + (!fieldName.isEmpty() ? fieldName + "$" : "")
        + sanitizeClassName(typeName)
        + "$"
        + sanitizeClassName(fieldTypeName);
  }

  static String getRealFieldName(String typeName, String fieldTypeName, String fieldName) {
    return "__opentelemetryVirtualField$"
        + (!fieldName.isEmpty() ? fieldName + "$" : "")
        + sanitizeClassName(typeName)
        + "$"
        + sanitizeClassName(fieldTypeName);
  }

  static String getRealGetterName(String typeName, String fieldTypeName, String fieldName) {
    return "__get" + getRealFieldName(typeName, fieldTypeName, fieldName);
  }

  static String getRealSetterName(String typeName, String fieldTypeName, String fieldName) {
    return "__set" + getRealFieldName(typeName, fieldTypeName, fieldName);
  }

  private static String sanitizeClassName(String className) {
    className = className.replace('.', '$');
    if (className.endsWith("[]")) {
      className = className.replace('[', '_').replace(']', '_');
    }
    return className;
  }
}
