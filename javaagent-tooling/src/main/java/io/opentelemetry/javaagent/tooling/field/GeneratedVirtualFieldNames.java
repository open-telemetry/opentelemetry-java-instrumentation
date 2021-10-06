/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import io.opentelemetry.javaagent.tooling.Utils;

final class GeneratedVirtualFieldNames {

  /**
   * Note: the value here has to be inside on of the prefixes in {@link
   * io.opentelemetry.javaagent.tooling.Constants#BOOTSTRAP_PACKAGE_PREFIXES}. This ensures that
   * 'isolating' (or 'module') classloaders like jboss and osgi see injected classes. This works
   * because we instrument those classloaders to load everything inside bootstrap packages.
   */
  static final String DYNAMIC_CLASSES_PACKAGE =
      "io.opentelemetry.javaagent.bootstrap.instrumentation.context.";

  private GeneratedVirtualFieldNames() {}

  static String getVirtualFieldImplementationClassName(String typeName, String fieldTypeName) {
    return DYNAMIC_CLASSES_PACKAGE
        + FieldBackedImplementationInstaller.class.getSimpleName()
        + "$VirtualField$"
        + Utils.convertToInnerClassName(typeName)
        + "$"
        + Utils.convertToInnerClassName(fieldTypeName);
  }

  static String getFieldAccessorInterfaceName(String typeName, String fieldTypeName) {
    return DYNAMIC_CLASSES_PACKAGE
        + FieldBackedImplementationInstaller.class.getSimpleName()
        + "$VirtualFieldAccessor$"
        + Utils.convertToInnerClassName(typeName)
        + "$"
        + Utils.convertToInnerClassName(fieldTypeName);
  }

  static String getRealFieldName(String fieldTypeName) {
    return "__opentelemetryVirtualField$" + Utils.convertToInnerClassName(fieldTypeName);
  }

  static String getRealGetterName(String fieldTypeName) {
    return "get" + getRealFieldName(fieldTypeName);
  }

  static String getRealSetterName(String fieldTypeName) {
    return "set" + getRealFieldName(fieldTypeName);
  }
}
