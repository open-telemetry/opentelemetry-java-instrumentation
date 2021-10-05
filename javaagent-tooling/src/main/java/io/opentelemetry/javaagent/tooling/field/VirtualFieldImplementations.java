/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getVirtualFieldImplementationClassName;

import java.util.Collection;
import java.util.Map;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;

final class VirtualFieldImplementations {

  // context-store-type-name -> context-store-type-name-dynamic-type
  private final Map<String, DynamicType.Unloaded<?>> virtualFieldImplementations;

  VirtualFieldImplementations(Map<String, DynamicType.Unloaded<?>> virtualFieldImplementations) {
    this.virtualFieldImplementations = virtualFieldImplementations;
  }

  TypeDescription find(String typeName, String fieldTypeName) {
    String virtualFieldImplementationClassName =
        getVirtualFieldImplementationClassName(typeName, fieldTypeName);
    DynamicType.Unloaded<?> type =
        virtualFieldImplementations.get(virtualFieldImplementationClassName);
    if (type == null) {
      throw new IllegalStateException(
          "Couldn't find VirtualField implementation class named "
              + virtualFieldImplementationClassName);
    }
    return type.getTypeDescription();
  }

  Collection<DynamicType.Unloaded<?>> getAllClasses() {
    return virtualFieldImplementations.values();
  }
}
