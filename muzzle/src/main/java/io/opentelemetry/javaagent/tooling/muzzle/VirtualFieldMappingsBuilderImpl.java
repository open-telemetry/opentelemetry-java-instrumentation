/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class VirtualFieldMappingsBuilderImpl implements VirtualFieldMappingsBuilder {
  private final Set<Map.Entry<String, String>> entrySet = new HashSet<>();

  @Override
  public VirtualFieldMappingsBuilder register(String typeName, String fieldTypeName) {
    entrySet.add(new AbstractMap.SimpleImmutableEntry<>(typeName, fieldTypeName));
    return this;
  }

  void registerAll(VirtualFieldMappings mappings) {
    entrySet.addAll(mappings.entrySet());
  }

  public VirtualFieldMappings build() {
    return new VirtualFieldMappings(entrySet);
  }
}
