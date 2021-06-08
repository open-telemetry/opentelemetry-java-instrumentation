/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.tooling.ignore.trie.Trie;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class IgnoredTypesBuilderImpl implements IgnoredTypesBuilder {
  private final Trie.Builder<IgnoreAllow> ignoreMatcherTrie = Trie.newBuilder();

  @Override
  public IgnoredTypesBuilder ignoreClass(String className) {
    ignoreMatcherTrie.put(className, IgnoreAllow.IGNORE);
    return this;
  }

  @Override
  public IgnoredTypesBuilder allowClass(String className) {
    ignoreMatcherTrie.put(className, IgnoreAllow.ALLOW);
    return this;
  }

  @Override
  public IgnoredTypesBuilder ignoreClassLoader(String classNameOrPrefix) {
    // TODO: collect classloader classes into a separate trie
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public IgnoredTypesBuilder allowClassLoader(String classNameOrPrefix) {
    // TODO: collect classloader classes into a separate trie
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public IgnoredTypesBuilder ignoreTaskClass(String className) {
    // TODO: collect task classes into a separate trie
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public IgnoredTypesBuilder allowTaskClass(String className) {
    // TODO: collect task classes into a separate trie
    throw new UnsupportedOperationException("not implemented yet");
  }

  public ElementMatcher<TypeDescription> buildIgnoredTypesMatcher() {
    return new IgnoredTypesMatcher(ignoreMatcherTrie.build());
  }
}
