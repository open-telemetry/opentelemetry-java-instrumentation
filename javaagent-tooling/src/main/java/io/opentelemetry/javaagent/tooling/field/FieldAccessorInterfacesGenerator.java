/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getFieldAccessorInterfaceName;
import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getRealGetterName;
import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getRealSetterName;

import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.SyntheticState;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;

final class FieldAccessorInterfacesGenerator {

  private final ByteBuddy byteBuddy;

  FieldAccessorInterfacesGenerator(ByteBuddy byteBuddy) {
    this.byteBuddy = byteBuddy;
  }

  FieldAccessorInterfaces generateFieldAccessorInterfaces(
      VirtualFieldMappings virtualFieldMappings) {
    Map<String, DynamicType.Unloaded<?>> fieldAccessorInterfaces =
        new HashMap<>(virtualFieldMappings.size());
    for (Map.Entry<String, String> entry : virtualFieldMappings.entrySet()) {
      DynamicType.Unloaded<?> type = makeFieldAccessorInterface(entry.getKey(), entry.getValue());
      fieldAccessorInterfaces.put(type.getTypeDescription().getName(), type);
    }
    return new FieldAccessorInterfaces(fieldAccessorInterfaces);
  }

  /**
   * Generate an interface that provides field accessor methods for given key class name and context
   * class name.
   *
   * @param typeName key class name
   * @param fieldTypeName context class name
   * @return unloaded dynamic type containing generated interface
   */
  private DynamicType.Unloaded<?> makeFieldAccessorInterface(
      String typeName, String fieldTypeName) {
    // We are using Object class name instead of fieldTypeName here because this gets injected
    // onto Bootstrap classloader where context class may be unavailable
    TypeDescription fieldTypeDesc = TypeDescription.OBJECT;
    return byteBuddy
        .makeInterface()
        .merge(SyntheticState.SYNTHETIC)
        .name(getFieldAccessorInterfaceName(typeName, fieldTypeName))
        .defineMethod(getRealGetterName(fieldTypeName), fieldTypeDesc, Visibility.PUBLIC)
        .withoutCode()
        .defineMethod(getRealSetterName(fieldTypeName), TypeDescription.VOID, Visibility.PUBLIC)
        .withParameter(fieldTypeDesc, "value")
        .withoutCode()
        .make();
  }
}
