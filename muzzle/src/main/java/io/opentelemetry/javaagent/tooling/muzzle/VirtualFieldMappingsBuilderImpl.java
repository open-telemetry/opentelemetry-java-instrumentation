/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.internal.RuntimeVirtualFieldSupplier;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings.Mapping;
import java.util.HashSet;
import java.util.Set;

public final class VirtualFieldMappingsBuilderImpl implements VirtualFieldMappingsBuilder {
  private final Set<Mapping> mappingSet = new HashSet<>();

  @Override
  @CanIgnoreReturnValue
  public VirtualFieldMappingsBuilder register(String typeName, String fieldTypeName) {
    return register(RuntimeVirtualFieldSupplier.DEFAULT_FIELD_NAME, typeName, fieldTypeName);
  }

  @Override
  public VirtualFieldMappingsBuilder register(
      String fieldName, String typeName, String fieldTypeName) {
    // since we are going to use the field name as part of generated class and method names we are
    // not going to allow all kinds of names
    if (!isIdentifier(fieldName)) {
      throw new IllegalArgumentException("Invalid field name: " + fieldName);
    }
    mappingSet.add(new Mapping(fieldName, typeName, fieldTypeName));
    return this;
  }

  private static boolean isIdentifier(String name) {
    if (name.isEmpty()) {
      return true;
    }

    int cp = name.codePointAt(0);
    if (!Character.isJavaIdentifierStart(cp)) {
      return false;
    }
    for (int i = Character.charCount(cp); i < name.length(); i += Character.charCount(cp)) {
      cp = name.codePointAt(i);
      if (!Character.isJavaIdentifierPart(cp)) {
        return false;
      }
    }
    return true;
  }

  void registerAll(VirtualFieldMappings mappings) {
    mappingSet.addAll(mappings.getMappings());
  }

  public VirtualFieldMappings build() {
    return new VirtualFieldMappings(mappingSet);
  }
}
