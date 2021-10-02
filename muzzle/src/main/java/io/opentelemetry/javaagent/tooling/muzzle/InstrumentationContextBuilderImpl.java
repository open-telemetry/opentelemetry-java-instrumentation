/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// TODO: we should rename the class so that it doesn't mention "Context", but "VirtualField"
// instead; probably once this class is hidden somewhere in the muzzle codegen contract
public final class InstrumentationContextBuilderImpl implements InstrumentationContextBuilder {
  private final Set<Map.Entry<String, String>> entrySet = new HashSet<>();

  @Override
  public InstrumentationContextBuilder register(String keyClassName, String contextClassName) {
    entrySet.add(new AbstractMap.SimpleImmutableEntry<>(keyClassName, contextClassName));
    return this;
  }

  void registerAll(ContextStoreMappings mappings) {
    entrySet.addAll(mappings.entrySet());
  }

  public ContextStoreMappings build() {
    return new ContextStoreMappings(entrySet);
  }
}
