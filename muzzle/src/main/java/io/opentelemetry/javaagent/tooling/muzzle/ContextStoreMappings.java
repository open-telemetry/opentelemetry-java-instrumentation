/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class ContextStoreMappings {
  private final Set<Map.Entry<String, String>> entrySet;

  public ContextStoreMappings(Set<Map.Entry<String, String>> entrySet) {
    this.entrySet = entrySet;
  }

  public int size() {
    return entrySet.size();
  }

  public boolean isEmpty() {
    return entrySet.isEmpty();
  }

  public boolean hasMapping(String keyClassName, String contextClassName) {
    return entrySet.contains(
        new AbstractMap.SimpleImmutableEntry<>(keyClassName, contextClassName));
  }

  public Set<Map.Entry<String, String>> entrySet() {
    return entrySet;
  }

  public void forEach(BiConsumer<String, String> action) {
    for (Map.Entry<String, String> e : entrySet) {
      action.accept(e.getKey(), e.getValue());
    }
  }
}
