/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getFieldAccessorInterfaceName;
import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getRealGetterName;
import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getRealSetterName;

import io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessorMarker;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings.Mapping;
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
    for (Mapping mapping : virtualFieldMappings.getMappings()) {
      DynamicType.Unloaded<?> type =
          makeFieldAccessorInterface(
              mapping.getFieldName(), mapping.getTypeName(), mapping.getFieldTypeName());
      fieldAccessorInterfaces.put(type.getTypeDescription().getName(), type);
    }
    return new FieldAccessorInterfaces(fieldAccessorInterfaces);
  }

  /**
   * Generate an interface that provides field accessor methods for given key class name and context
   * class name.
   *
   * @param fieldName field name
   * @param typeName key class name
   * @param fieldTypeName context class name
   * @return unloaded dynamic type containing generated interface
   */
  private DynamicType.Unloaded<?> makeFieldAccessorInterface(
      String fieldName, String typeName, String fieldTypeName) {
    // We are using Object class name instead of fieldTypeName here because this gets injected
    // onto the bootstrap class loader where context class may be unavailable
    TypeDescription fieldTypeDesc = TypeDescription.ForLoadedType.of(Object.class);
    return byteBuddy
        .makeInterface()
        .merge(SyntheticState.SYNTHETIC)
        .name(getFieldAccessorInterfaceName(fieldName, typeName, fieldTypeName))
        .implement(VirtualFieldAccessorMarker.class)
        .defineMethod(
            getRealGetterName(fieldName, typeName, fieldTypeName),
            fieldTypeDesc,
            Visibility.PUBLIC,
            SyntheticState.SYNTHETIC)
        .withoutCode()
        .defineMethod(
            getRealSetterName(fieldName, typeName, fieldTypeName),
            TypeDescription.ForLoadedType.of(void.class),
            Visibility.PUBLIC,
            SyntheticState.SYNTHETIC)
        .withParameter(fieldTypeDesc, "value")
        .withoutCode()
        .make();
  }
}
