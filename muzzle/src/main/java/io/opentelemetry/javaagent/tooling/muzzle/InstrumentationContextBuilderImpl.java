/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationContextBuilder;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
