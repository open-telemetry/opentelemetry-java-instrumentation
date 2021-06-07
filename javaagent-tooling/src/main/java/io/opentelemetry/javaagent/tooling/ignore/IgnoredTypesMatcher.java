/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import io.opentelemetry.javaagent.tooling.ignore.trie.Trie;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class IgnoredTypesMatcher extends ElementMatcher.Junction.AbstractBase<TypeDescription> {
  private final Trie<IgnoreAllow> ignoredTypes;

  IgnoredTypesMatcher(Trie<IgnoreAllow> ignoredTypes) {
    this.ignoredTypes = ignoredTypes;
  }

  @Override
  public boolean matches(TypeDescription target) {
    IgnoreAllow ignored = ignoredTypes.getOrNull(target.getActualName());
    // ALLOW or null (default) mean that the type should not be ignored
    return ignored == IgnoreAllow.IGNORE;
  }
}
