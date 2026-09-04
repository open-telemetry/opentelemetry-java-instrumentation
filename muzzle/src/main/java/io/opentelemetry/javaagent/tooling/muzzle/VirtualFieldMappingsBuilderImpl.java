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
      String filedName, String typeName, String fieldTypeName) {
    mappingSet.add(new Mapping(filedName, typeName, fieldTypeName));
    return this;
  }

  void registerAll(VirtualFieldMappings mappings) {
    mappingSet.addAll(mappings.getMappings());
  }

  public VirtualFieldMappings build() {
    return new VirtualFieldMappings(mappingSet);
  }
}
