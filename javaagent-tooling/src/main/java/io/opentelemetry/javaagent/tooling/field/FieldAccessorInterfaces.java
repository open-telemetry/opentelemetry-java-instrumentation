/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getFieldAccessorInterfaceName;

import java.util.Collection;
import java.util.Map;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;

final class FieldAccessorInterfaces {

  // field-accessor-interface-name -> fields-accessor-interface-dynamic-type
  private final Map<String, DynamicType.Unloaded<?>> fieldAccessorInterfaces;

  FieldAccessorInterfaces(Map<String, DynamicType.Unloaded<?>> fieldAccessorInterfaces) {
    this.fieldAccessorInterfaces = fieldAccessorInterfaces;
  }

  TypeDescription find(String typeName, String fieldTypeName) {
    String accessorInterfaceName = getFieldAccessorInterfaceName(typeName, fieldTypeName);
    DynamicType.Unloaded<?> type = fieldAccessorInterfaces.get(accessorInterfaceName);
    if (type == null) {
      throw new IllegalStateException(
          "Couldn't find field accessor interface named " + accessorInterfaceName);
    }
    return type.getTypeDescription();
  }

  Collection<DynamicType.Unloaded<?>> getAllInterfaces() {
    return fieldAccessorInterfaces.values();
  }
}
